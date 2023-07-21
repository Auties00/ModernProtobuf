package it.auties.protobuf.serialization;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

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
        var className = qualifiedElementName.replace(packageName + ".", "");
        var outputFile = getOutputFile(outputDirectory, packageName, className);
        var classReader = redefineClass(outputFile);
        var classWriter = new ClassWriter(classReader, 0);
        var messageVisitor = new ProtobufMessageVisitor();
        classReader.accept(messageVisitor, 0);
        var messageName = messageVisitor.messageName();
        if(messageName.isEmpty()){
            return;
        }

        var serializerVisitor = new ProtobufDeserializationVisitor(messageName.get(), messageVisitor.fieldsPropertyValuesMap(), classWriter);
        classReader.accept(serializerVisitor, 0);
        System.out.println("Done: " + messageVisitor.fieldsPropertyValuesMap());
    }

    private ClassReader redefineClass(Path pathToClassFile) {
       try {
           var read = Files.readAllBytes(pathToClassFile);
           return new ClassReader(read);
       }catch (IOException throwable) {
           throw new UncheckedIOException("Cannot read class to redefine", throwable);
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
