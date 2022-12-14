package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.statement.ProtobufDocument;
import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufObject;
import lombok.SneakyThrows;
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
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    public List<Path> generate(Factory factory, boolean accessors) {
        return document.statements()
                .stream()
                .map(statement -> generate(statement, accessors, factory))
                .toList();
    }

    public Path generate(ProtobufObject<?> object,  boolean accessors, Factory factory) {
        Objects.requireNonNull(directory, "Cannot generate files without a target directory");
        var path = Path.of(directory.getPath(), "/%s.java".formatted(object.name()));
        return generate(object, accessors, factory, path);
    }

    public Path generate(ProtobufObject<?> object, boolean accessors, Factory factory, Path path) {
        var schemaCreator = findGenerator(object, accessors, factory);
        var schema = schemaCreator.createSchema();
        sortMembers(schema);
        return writeFile(path, schema.toStringWithImports());
    }

    private SchemaCreator<?, ?> findGenerator(ProtobufObject<?> object,  boolean accessors, Factory factory) {
        if (object instanceof ProtobufMessageStatement msg) {
            return new MessageSchemaCreator(msg, accessors, factory);
        }

        if (object instanceof ProtobufEnumStatement enm) {
            return new EnumSchemaCreator(enm, accessors, factory);
        }

        throw new IllegalArgumentException("Cannot find a schema generator for statement %s(%s)".formatted(object.name(), object.getClass().getName()));
    }

    @SneakyThrows
    public void update(CtType<?> element, ProtobufObject<?> statement, Path path, boolean accessors, boolean forceUpdate) {
        var originalType = element.clone();
        originalType.setAnnotations(new ArrayList<>(originalType.getAnnotations()));
        var schemaCreator = findSchemaUpdater(element, statement, accessors);
        var schema = schemaCreator.update();
        if(Objects.equals(originalType, schema) && !forceUpdate){
            return;
        }

        sortMembers(schema);
        var result = schema.toString();
        writeFile(
                path,
                withOldImports(Files.readString(path), result)
        );
    }

    private String withOldImports(String oldMeta, String newMeta){
        var packageMatcher = Pattern.compile("(?<=package )(.*)(?=;)")
                .matcher(oldMeta)
                .results()
                .findFirst()
                .map(MatchResult::group)
                .map("package %s;\n"::formatted)
                .orElse("");
        var importMatcher = Pattern.compile("(?<=import )(.*)(?=;)")
                .matcher(oldMeta)
                .results()
                .map(MatchResult::group)
                .map("import %s;"::formatted)
                .collect(Collectors.toSet());
        importMatcher.add("import it.auties.protobuf.base.ProtobufName;");
        importMatcher.add("import it.auties.protobuf.base.ProtobufType;");
        importMatcher.add("import lombok.*;");
        importMatcher.add("import lombok.experimental.*;");
        importMatcher.add("import static it.auties.protobuf.base.ProtobufType.*;");
        importMatcher.add("import java.util.*;");
        var imports = String.join("\n", importMatcher);
        return "%s%s%s".formatted(
                packageMatcher,
                imports.isBlank() ? imports : "%s\n".formatted(imports),
                newMeta
        );
    }

    private SchemaCreator<?, ?> findSchemaUpdater(CtType<?> element, ProtobufObject<?> statement, boolean accessors) {
        if (statement instanceof ProtobufMessageStatement msg) {
            return new MessageSchemaCreator((CtClass<?>) element, msg, accessors, element.getFactory());
        }

        if (statement instanceof ProtobufEnumStatement enm) {
            return new EnumSchemaCreator((CtEnum<?>) element, enm, accessors, element.getFactory());
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
