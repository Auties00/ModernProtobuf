package it.auties.protobuf.tool.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@UtilityClass
public class AstUtils implements LogProvider {
    public List<CompilationUnit> createClassPool(File directory) {
        if(directory == null){
            return List.of();
        }

        try(var walker = Files.walk(directory.toPath())) {
            return walker.filter(entry -> entry.endsWith(".java"))
                    .map(AstUtils::parseClass)
                    .toList();
        }catch (IOException exception){
            throw new RuntimeException("Cannot create class pool", exception);
        }
    }

    private CompilationUnit parseClass(Path path) {
        try {
            return StaticJavaParser.parse(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
