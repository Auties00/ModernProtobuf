package it.auties.protobuf.schema.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class AstUtils implements LogProvider {
    public static List<CompilationUnit> createClassPool(File directory) {
        if(directory == null){
            return List.of();
        }

        try(var walker = Files.walk(directory.toPath())) {
            return walker.filter(entry -> entry.endsWith(".java"))
                    .map(AstUtils::parseClass)
                    .flatMap(Optional::stream)
                    .toList();
        }catch (IOException exception){
            throw new RuntimeException("Cannot create class pool", exception);
        }
    }

    private static Optional<CompilationUnit> parseClass(Path path) {
        try {
            return Optional.of(StaticJavaParser.parse(path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
