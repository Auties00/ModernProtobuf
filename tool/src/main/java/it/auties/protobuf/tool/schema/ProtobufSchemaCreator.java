package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.object.ProtobufDocument;
import it.auties.protobuf.parser.object.ProtobufObject;
import it.auties.protobuf.parser.statement.EnumStatement;
import it.auties.protobuf.parser.statement.MessageStatement;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public record ProtobufSchemaCreator(ProtobufDocument document, String pack, File directory) {
    public void generateSchema() {
        for (var protobufObject : document.getStatements()) {
            generateSchema(protobufObject);
        }
    }

    private void generateSchema(ProtobufObject<?> object) {
        var schemaCreator = findSchemaGenerator(object);
        var schema = schemaCreator.createSchema(0);
        writeFile(object, schema);
    }

    private SchemaCreator findSchemaGenerator(ProtobufObject<?> object) {
        if (object instanceof MessageStatement msg) {
            return new MessageSchemaCreator(msg, pack);
        }

        if (object instanceof EnumStatement enm) {
            return new EnumSchemaCreator(enm, pack);
        }

        throw new IllegalArgumentException("Cannot find a schema generator for statement %s(%s)".formatted(object.name(), object.getClass().getName()));
    }

    private void writeFile(ProtobufObject<?> object, String formattedSchema) {
        try {
            var path = Path.of(directory.getPath(), "/%s.java".formatted(object.name()));
            Files.write(path, formattedSchema.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }catch (IOException exception){
            throw new UncheckedIOException("Cannot write schema to file", exception);
        }
    }
}
