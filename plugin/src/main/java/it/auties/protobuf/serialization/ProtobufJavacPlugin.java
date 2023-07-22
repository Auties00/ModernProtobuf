package it.auties.protobuf.serialization;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ProtobufJavacPlugin implements Plugin, TaskListener{
    private String outputDirectory;

    @Override
    public void init(JavacTask task, String... args) {
        parseArgs(args);
        System.err.println("Started");
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
        var packageName = getPackageName(event);
        var qualifiedElementName = getQualifiedElementName(event);
        var simpleClassName = qualifiedElementName.replace(packageName + ".", "");
        var outputFile = getOutputFile(outputDirectory, packageName, simpleClassName);
        var classReader = getClassReader(outputFile);
        if(classReader == null) {
            return;
        }

        var element = createProtoElement(classReader);
        if(element == null) {
            return;
        }

        var classWriter = new ClassWriter(classReader, 0);
        var deserializationVisitor = new ProtobufDeserializationVisitor(element, classWriter);
        classReader.accept(deserializationVisitor, 0);
        writeResult(classWriter, outputFile);
    }

    private void writeResult(ClassWriter classWriter, Path outputFile) {
        try {
            var result = classWriter.toByteArray();
            Files.write(outputFile, result);
        }catch (IOException exception) {
            throw new UncheckedIOException("Cannot write instrumented class", exception);
        }
    }

    private ProtobufMessageElement createProtoElement(ClassReader classReader) {
        var classNode = new ClassNode();
        classReader.accept(classNode,0);
        if(!isProtoMessage(classNode)) {
            return null;
        }

        var element = new ProtobufMessageElement(toCanonicalName(classNode.name), isEnum(classNode.access));
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

                var type = Type.getType(field.desc);
                var values = getDefaultPropertyValues();
                annotation.accept(new ProtobufPropertyVisitor(values));
                element.addField(type.getClassName(), field.name, values);
            }
        }
    }

    private void getEnumConstants(ClassNode classNode, ProtobufMessageElement element) {
        var clInitMethod = classNode.methods
                .stream()
                .filter(entry -> entry.name.equals("<clinit>"))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Missing <clinit> method in enum declaration: corrupted bytecode"));
        var analyzer = new ProtobufAnalyzerAdapter(element, clInitMethod);
        clInitMethod.accept(analyzer);
    }

    private boolean isEnumConstant(ProtobufMessageElement element, AbstractInsnNode entry) {
        return entry instanceof FieldInsnNode fieldNode
                && Objects.equals(Type.getType(fieldNode.desc).getClassName(), element.className());
    }

    private TreeMap<String, Object> getDefaultPropertyValues() {
        return Arrays.stream(ProtobufProperty.class.getMethods())
                .map(entry -> entry.getDefaultValue() != null ? Map.entry(entry.getName(), entry.getDefaultValue()) : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, TreeMap::new));
    }

    private boolean isProtoMessage(ClassNode classNode) {
        return !isAbstract(classNode)
                && classNode.interfaces.stream().anyMatch(entry -> Objects.equals(toCanonicalName(entry), ProtobufMessage.class.getName()));
    }
    
    private boolean isAbstract(ClassNode node) {
        return (node.access & Opcodes.ACC_INTERFACE) != 0
                || (node.access & Opcodes.ACC_ABSTRACT) != 0;
    }

    private boolean isEnum(int access) {
        return (access & Opcodes.ACC_ENUM) != 0;
    }

    private String toCanonicalName(String entry) {
        return entry.replaceAll("/", ".");
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
        var packageLocation = packageName.replaceAll("\\.", File.separator);
        var innerClass = className.replaceAll("\\.", "\\$");
        return Path.of(outputDirectory + packageLocation + File.separator + innerClass + ".class");
    }

    private String getQualifiedElementName(TaskEvent event) {
        var result = event.getTypeElement()
                .getQualifiedName()
                .toString();
        return result.endsWith(".")
                ? result.substring(0, result.length() - 1)
                : result;
    }

    private String getPackageName(TaskEvent event) {
        return event.getCompilationUnit()
                .getPackageName()
                .toString();
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

        throw new IllegalArgumentException("Inference for output file failed: " + path);
    }
}
