package it.auties.protobuf.schema.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class AstUtils implements LogProvider {
    public static String toJavaName(String name) {
        if(!SourceVersion.isName(name)) {
            return "_" + name;
        }

        return name;
    }

    public static String toCanonicalJavaName(String name) {
        return name.replaceAll("\\$", ".");
    }

    public static List<CompilationUnit> createClassPool(File directory) {
        if(directory == null){
            return List.of();
        }

        try(var walker = Files.walk(directory.toPath())) {
            return walker.filter(entry -> entry.toString().endsWith(".java"))
                    .map(AstUtils::parseClass)
                    .flatMap(Optional::stream)
                    .toList();
        }catch (IOException exception){
            throw new RuntimeException("Cannot create class pool", exception);
        }
    }

    private static Optional<CompilationUnit> parseClass(Path path) {
        try {
            var config = new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
            var parser = new JavaParser(config);
            return parser.parse(path).getResult();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
