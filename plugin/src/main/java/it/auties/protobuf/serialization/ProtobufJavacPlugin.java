package it.auties.protobuf.serialization;

import com.sun.source.util.*;
import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public class ProtobufJavacPlugin implements Plugin, TaskListener{
    private String outputDirectory;

    private Set<Class<?>> classes;

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

    @SneakyThrows
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
        var byteBuddy = loadClass(outputFile);
        System.out.println(outputFile.toString() + Files.exists(outputFile));
    }

    private DynamicType.Builder<?> loadClass(Path pathToClassFile) throws Exception {
        var defineMethod = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
        defineMethod.setAccessible(true);
        var read = Files.readAllBytes(pathToClassFile);
        var loadedClass = (Class<?>) defineMethod.invoke(ProtobufJavacPlugin.class.getClassLoader(), null, read, 0, read.length);
        return new ByteBuddy().redefine(loadedClass);
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
