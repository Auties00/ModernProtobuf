package it.auties.protobuf.serialization;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.analysis.ProtobufEnumAnalyzer;
import it.auties.protobuf.serialization.analysis.ProtobufPropertyAnalyzer;
import it.auties.protobuf.serialization.instrumentation.ProtobufDeserializationVisitor;
import it.auties.protobuf.serialization.instrumentation.ProtobufSerializationVisitor;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ProtobufJavacPlugin implements Plugin, TaskListener{
    private final Map<String, byte[]> javaToSourceMap = new HashMap<>();
    private final Map<Path, List<ProtobufMessageElement>> missingTypesToElementsMap = new HashMap<>();
    private final Map<ProtobufMessageElement, List<Path>> elementsToMissingTypesMap = new HashMap<>();
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
        var classReader = getClassReaderResult(outputFile).orElse(null);
        if(classReader == null) {
            return;
        }

        var element = createProtoElement(classReader.classReader(), outputDirectory)
                .filter(ProtobufMessageElement::isProtobuf)
                .orElse(null);
        if(element == null) {
            return;
        }

        processElement(element, outputFile);
        classReader.dependentElements()
                .forEach(dependentElement -> processElement(dependentElement, outputFile));
    }

    private void processElement(ProtobufMessageElement element, Path outputFile) {
        element.checkErrors();
        var classWriter = new ClassWriter(element.classReader(), ClassWriter.COMPUTE_MAXS);
        element.classReader().accept(classWriter, 0);
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
            Files.write(outputFile, classWriter.toByteArray());
        }catch (IOException exception) {
            throw new UncheckedIOException("Cannot write instrumented class", exception);
        }
    }

    private Optional<ProtobufMessageElement> createProtoElement(ClassReader classReader, String output) {
        var classNode = new ClassNode();
        classReader.accept(classNode, 0);
        var element = new ProtobufMessageElement(classNode, classReader);
        if(element.isEnum()) {
            getEnumConstants(classNode, element);
            return Optional.of(element);
        }

        return Optional.of(element)
                .filter(entry -> getMessageFields(classNode, entry, output));
    }

    private boolean getMessageFields(ClassNode classNode, ProtobufMessageElement element, String output) {
        var completeType = true;
        for(var field : classNode.fields) {
            if(field.visibleAnnotations == null) {
                continue;
            }

            var missingTypes = new ArrayList<Path>();
            for(var annotation : field.visibleAnnotations) {
                var annotationType = Type.getType(annotation.desc);
                if (!Objects.equals(annotationType.getClassName(), ProtobufProperty.class.getName())) {
                    continue;
                }

                var values = getDefaultPropertyValues();
                annotation.accept(new ProtobufPropertyAnalyzer(values));
                var property = element.addProperty(field.name, field.desc, field.signature, values)
                        .orElse(null);
                if(property == null || (property.protoType() != ProtobufType.MESSAGE && property.protoType() != ProtobufType.ENUM)) {
                    continue;
                }

                var path = Path.of(output + property.javaType().getInternalName() + ".class");
                if(Files.exists(path)) {
                    var classReaderResult = getClassReaderResult(path)
                            .orElseThrow(() -> new IllegalArgumentException("Cannot read: " + path));
                    classReaderResult.dependentElements()
                            .forEach(dependentElement -> processElement(dependentElement, Path.of(output)));
                    property.localJavaType().set(classReaderResult.classReader());
                    continue;
                }

                var missingElements = Objects.requireNonNullElseGet(missingTypesToElementsMap.get(path), ArrayList<ProtobufMessageElement>::new);
                missingElements.add(element);
                missingTypesToElementsMap.put(path, missingElements);
                missingTypes.add(path);
                completeType = false;
            }

            if(missingTypes.isEmpty()) {
                continue;
            }

            elementsToMissingTypesMap.put(element, missingTypes);
        }

        return completeType;
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

    private Optional<ClassReaderResult> getClassReaderResult(Path source) {
       try {
           var cached = javaToSourceMap.get(source.toString());
           if(cached != null) {
               return Optional.of(new ClassReaderResult(new ClassReader(cached), List.of()));
           }

           var read = Files.readAllBytes(source);
           javaToSourceMap.put(source.toString(), read);
           var result = new ClassReader(read);
           var dependentElements = getDependentElements(source);
           return Optional.of(new ClassReaderResult(result, dependentElements));
       }catch (IOException throwable) {
           return Optional.empty();
       }
    }

    private List<ProtobufMessageElement> getDependentElements(Path source) {
        var missingTypes = missingTypesToElementsMap.remove(source);
        return missingTypes == null ? List.of() : missingTypes.stream()
                .filter(element -> isElementReady(source, element))
                .toList();
    }

    private boolean isElementReady(Path source, ProtobufMessageElement element) {
        var missingElements = elementsToMissingTypesMap.get(element);
        missingElements.remove(source);
        return missingElements.isEmpty();
    }

    private record ClassReaderResult(ClassReader classReader, List<ProtobufMessageElement> dependentElements) {

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
