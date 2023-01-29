package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.statement.ProtobufDocument;
import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.tool.util.AstWriter;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;

public record ProtobufSchemaCreator(ProtobufDocument document, File directory) {
    public ProtobufSchemaCreator(ProtobufDocument document){
        this(document, null);
    }

    public void generate(Factory factory, boolean accessors) {
        document.statements()
                .forEach(statement -> generate(statement, accessors, factory));
    }

    public void generate(ProtobufObject<?> object,  boolean accessors, Factory factory) {
        Objects.requireNonNull(directory, "Cannot generate files without a target directory");
        var path = Path.of(directory.getPath(), "/%s.java".formatted(object.name()));
        generate(object, accessors, factory, path);
    }

    public void generate(ProtobufObject<?> object, boolean accessors, Factory factory, Path path) {
        if (object instanceof ProtobufMessageStatement msg) {
            var schema = new MessageSchemaCreator(msg, accessors, factory);
            var result = schema.generate();
            AstWriter.write(result, path);
            return;
        }

        if (object instanceof ProtobufEnumStatement enm) {
            var schema = new EnumSchemaCreator(enm, accessors, factory);
            var result = schema.generate();
            AstWriter.write(result, path);
            return;
        }

        throw new IllegalArgumentException("Cannot find a schema generator for statement %s(%s)".formatted(object.name(), object.getClass().getName()));
    }

    public void update(CtType<?> element, ProtobufObject<?> statement, boolean accessors, Path output) {
        if (statement instanceof ProtobufMessageStatement msg) {
            var schema = new MessageSchemaCreator((CtClass<?>) element, msg, accessors);
            schema.update(false);
            AstWriter.writeLazy(element, msg, output);
            return;
        }

        if (statement instanceof ProtobufEnumStatement enm) {
            var schema = new EnumSchemaCreator((CtEnum<?>) element, enm, accessors);
            schema.update(false);
            AstWriter.writeLazy(element, enm, output);
            return;
        }

        throw new IllegalArgumentException("Cannot find a schema updater for statement");
    }
}
