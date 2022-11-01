package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.statement.ProtobufDocument;
import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufObject;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.support.reflect.declaration.CtClassImpl;
import spoon.support.reflect.declaration.CtEnumImpl;
import spoon.support.reflect.declaration.CtFieldImpl;
import spoon.support.reflect.declaration.CtMethodImpl;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ProtobufSchemaCreator(ProtobufDocument document, File directory) {
    private static final List<Class<?>> ORDER = List.of(
            CtFieldImpl.class,
            CtEnumValue.class,
            CtMethodImpl.class,
            CtEnumImpl.class,
            CtClassImpl.class
    );

    public ProtobufSchemaCreator(ProtobufDocument document){
        this(document, null);
    }

    public List<Path> generate(Factory factory) {
        return document.statements()
                .stream()
                .map(statement -> generate(statement, factory))
                .toList();
    }

    public Path generate(ProtobufObject<?> object, Factory factory) {
        Objects.requireNonNull(directory, "Cannot generate files without a target directory");
        var path = Path.of(directory.getPath(), "/%s.java".formatted(object.name()));
        return generate(object, factory, path);
    }

    public Path generate(ProtobufObject<?> object, Factory factory, Path path) {
        var schemaCreator = findGenerator(object, factory);
        var schema = schemaCreator.createSchema();
        sortMembers(schema);
        return writeFile(path, schema.toStringWithImports());
    }

    private SchemaCreator<?, ?> findGenerator(ProtobufObject<?> object, Factory factory) {
        if (object instanceof ProtobufMessageStatement msg) {
            return new MessageSchemaCreator(msg, factory);
        }

        if (object instanceof ProtobufEnumStatement enm) {
            return new EnumSchemaCreator(enm, factory);
        }

        throw new IllegalArgumentException("Cannot find a schema generator for statement %s(%s)".formatted(object.name(), object.getClass().getName()));
    }

    public void update(CtType<?> element, ProtobufObject<?> statement, Path path) {
        var schemaCreator = findSchemaUpdater(element, statement);
        var schema = schemaCreator.update();
        sortMembers(schema);
        writeFile(path, schema.toStringWithImports());
    }

    private SchemaCreator<?, ?> findSchemaUpdater(CtType<?> element, ProtobufObject<?> statement) {
        if (statement instanceof ProtobufMessageStatement msg) {
            return new MessageSchemaCreator((CtClass<?>) element, msg, element.getFactory());
        }

        if (statement instanceof ProtobufEnumStatement enm) {
            return new EnumSchemaCreator((CtEnum<?>) element, enm, element.getFactory());
        }

        throw new IllegalArgumentException("Cannot find a schema updater for statement");
    }

    private Path writeFile(Path path, String formattedSchema) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, formattedSchema,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return path;
        }catch (IOException exception){
            throw new UncheckedIOException("Cannot write schema to file", exception);
        }
    }

    private void sortMembers(CtType<?> schema) {
        var parsed = schema.getTypeMembers()
                .stream()
                .sorted(Comparator.comparingInt((entry) -> ORDER.indexOf(entry.getClass())))
                .toList();
        schema.setTypeMembers(parsed);
    }
}
