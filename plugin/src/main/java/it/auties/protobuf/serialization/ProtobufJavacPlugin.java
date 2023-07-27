package it.auties.protobuf.serialization;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.serialization.analysis.ProtobufEnumAnalyzer;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.instrumentation.ProtobufDeserializationVisitor;
import it.auties.protobuf.serialization.analysis.ProtobufPropertyAnalyzer;
import it.auties.protobuf.serialization.instrumentation.ProtobufSerializationVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class ProtobufJavacPlugin implements Plugin, TaskListener{
    private String outputDirectory;

    @Override
    public void init(JavacTask task, String... args) {
        parseArgs(args);
        task.addTaskListener(this);
    }

    private void parseArgs(String[] args) {
        if(args.length > 1){
            throw new IllegalArgumentException("Unexpected arguments: " + Arrays.toString(args));
        }

        this.outputDirectory = getOutputDirectory(args)
                .orElse(null);
    }

    private Optional<String> getOutputDirectory(String[] args){
        if(args.length == 0){
            return Optional.empty();
        }

        var result = args[1];
        return result.endsWith(File.separator)
                ? Optional.of(result)
                : Optional.of(result + File.separator);
    }

    @Override
    public String getName() {
        return "protobuf";
    }

    @Override
    public boolean autoStart() {
        return true;
    }

    @Override
    public void finished(TaskEvent event) {
        if(event.getKind() != TaskEvent.Kind.GENERATE){
            return;
        }

        var outputDirectory = getOutputDirectory(event);
        var packageName = getPackageName(event).orElse(null);
        var simpleClassName = getSimpleClassName(event, packageName);
        var outputFile = getOutputFile(outputDirectory, packageName, simpleClassName);
        var classReader = getClassReader(outputFile);
        if(classReader == null) {
            return;
        }

        var element = createProtoElement(classReader);
        if(!element.isProtobuf()) {
            return;
        }

        element.checkErrors();
        var classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
        classReader.accept(classWriter, 0);
        if(!element.isEnum()){
            createSerializer(classWriter, element);
        }

        createDeserializer(classWriter, element);
        writeResult(classWriter, outputFile);
    }

    private void createSerializer(ClassWriter classWriter, ProtobufMessageElement element) {
        var serializationVisitor = new ProtobufSerializationVisitor(element, classWriter);
        serializationVisitor.instrument();
    }

    private void createDeserializer(ClassWriter classWriter, ProtobufMessageElement element) {
        var deserializationVisitor = new ProtobufDeserializationVisitor(element, classWriter);
        deserializationVisitor.instrument();
    }

    private void writeResult(ClassWriter classWriter, Path outputFile) {
        try {
            var result = classWriter.toByteArray();
            Files.write(outputFile, result, StandardOpenOption.TRUNCATE_EXISTING);
        }catch (IOException exception) {
            throw new UncheckedIOException("Cannot write instrumented class", exception);
        }
    }

    private ProtobufMessageElement createProtoElement(ClassReader classReader) {
        var classNode = new ClassNode();
        classReader.accept(classNode, 0);
        var element = new ProtobufMessageElement(classNode);
        if(element.isEnum()) {
            getEnumConstants(classNode, element);
            return element;
        }

        getMessageFields(classNode, element);
        return element;
    }

    private void getMessageFields(ClassNode classNode, ProtobufMessageElement element) {
        for(var field : classNode.fields) {
            if(field.visibleAnnotations == null) {
                continue;
            }

            for(var annotation : field.visibleAnnotations) {
                var annotationType = Type.getType(annotation.desc);
                if (!Objects.equals(annotationType.getClassName(), ProtobufProperty.class.getName())) {
                    continue;
                }

                var values = getDefaultPropertyValues();
                annotation.accept(new ProtobufPropertyAnalyzer(values));
                element.addProperty(field.name, field.desc, field.signature, values);
            }
        }
    }

    private void getEnumConstants(ClassNode classNode, ProtobufMessageElement element) {
        var clInitMethod = classNode.methods
                .stream()
                .filter(entry -> entry.name.equals("<clinit>"))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Missing <clinit> method in enum declaration: corrupted bytecode"));
        var analyzer = new ProtobufEnumAnalyzer(element, clInitMethod);
        clInitMethod.accept(analyzer);
    }

    private TreeMap<String, Object> getDefaultPropertyValues() {
        return Arrays.stream(ProtobufProperty.class.getMethods())
                .map(entry -> entry.getDefaultValue() != null ? Map.entry(entry.getName(), entry.getDefaultValue()) : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, TreeMap::new));
    }

    private ClassReader getClassReader(Path pathToClassFile) {
       try {
           var read = Files.readAllBytes(pathToClassFile);
           return new ClassReader(read);
       }catch (IOException throwable) {
           return null; // This happens for example in anonymous inner classes
       }
    }

    private Path getOutputFile(String outputDirectory, String packageName, String className) {
        var packagePart = packageName == null ? "" : packageName.replaceAll("\\.", File.separator) + File.separator;
        var innerClass = className.replaceAll("\\.", "\\$");
        return Path.of(outputDirectory + packagePart + innerClass + ".class");
    }

    private String getSimpleClassName(TaskEvent event, String packageName) {
        var rawQualifiedName = event.getTypeElement()
                .getQualifiedName()
                .toString();
        var qualifiedName = rawQualifiedName.endsWith(".")
                ? rawQualifiedName.substring(0, rawQualifiedName.length() - 1)
                : rawQualifiedName;
        return packageName == null ? qualifiedName
                : qualifiedName.replace(packageName + ".", "");
    }

    private Optional<String> getPackageName(TaskEvent event) {
        return Optional.ofNullable(event.getCompilationUnit().getPackageName())
                .map(Objects::toString);
    }


    // Can't use FileManager to get the location without inference as that API is not public
    private String getOutputDirectory(TaskEvent event) {
        if(outputDirectory != null){
            return outputDirectory;
        }

        var path = event.getSourceFile().getName();
        var mainIndex = path.indexOf("src/main/java/");
        if(mainIndex != 0) {
            return path.substring(0, mainIndex) + "target/classes/";
        }

        var testIndex = path.indexOf("src/test/java");
        if(testIndex != 0) {
            return path.substring(0, testIndex) + "target/classes/";
        }

        throw new IllegalArgumentException("Inference for output file failed. Please specify the output path manually as a plugin parameter");
    }
}
