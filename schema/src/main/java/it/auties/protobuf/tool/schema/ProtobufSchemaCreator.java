package it.auties.protobuf.tool.schema;

import com.github.javaparser.ast.CompilationUnit;
import it.auties.protobuf.parser.statement.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record ProtobufSchemaCreator(ProtobufDocument document, File directory) {
    public ProtobufSchemaCreator(ProtobufDocument document){
        this(document, null);
    }

    public void generate(List<CompilationUnit> classPool, boolean mutable) {
        document.statements()
                .stream()
                .collect(Collectors.toMap(ProtobufStatement::qualifiedPath, statement -> generate(statement, mutable, classPool)))
                .forEach(this::writeOrThrow);
    }

    private void writeOrThrow(String path, CompilationUnit unit) {
        try {
            Files.writeString(directory.toPath().resolve(path + ".java"), unit.toString());
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot write output", exception);
        }
    }

    public CompilationUnit generate(ProtobufObject<?> object, boolean mutable, List<CompilationUnit> classPool) {
        Objects.requireNonNull(directory, "Cannot generate files without a target directory");
        var output = Path.of(directory.getPath(), "/%s.java".formatted(object.name()));
        if (object instanceof ProtobufMessageStatement msg) {
            var schema = new MessageSchemaCreator(msg, mutable, classPool);
            return schema.generate(output);
        }

        if (object instanceof ProtobufEnumStatement enm) {
            var schema = new EnumSchemaCreator(enm, mutable, classPool);
            return schema.generate(output);
        }

        throw new IllegalArgumentException("Cannot find a schema generator for statement %s(%s)".formatted(object.name(), object.getClass().getName()));
    }

    public void update(ProtobufObject<?> statement, boolean mutable, List<CompilationUnit> classPool, Path output) {
        if (statement instanceof ProtobufMessageStatement msg) {
            var schema = new MessageSchemaCreator(msg, mutable, classPool);
            schema.update(output);
            return;
        }

        if (statement instanceof ProtobufEnumStatement enm) {
            var schema = new EnumSchemaCreator(enm, mutable, classPool);
            schema.update(output);
            return;
        }

        throw new IllegalArgumentException("Cannot find a schema updater for statement");
    }
}
