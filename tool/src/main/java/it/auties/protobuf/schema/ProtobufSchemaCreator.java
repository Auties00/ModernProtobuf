package it.auties.protobuf.schema;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import it.auties.protobuf.ast.EnumStatement;
import it.auties.protobuf.ast.MessageStatement;
import it.auties.protobuf.ast.ProtobufDocument;
import it.auties.protobuf.ast.ProtobufObject;
import lombok.AllArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@AllArgsConstructor
public class ProtobufSchemaCreator {
    private final ProtobufDocument document;
    private final String pack;
    private final File directory;
    private final Formatter formatter;

    public void generateSchema() throws IOException, FormatterException, ClassNotFoundException {
        for (var protobufObject : document.getStatements()) {
            generateSchema(protobufObject);
        }
    }

    private void generateSchema(ProtobufObject<?> object) throws IOException, FormatterException, ClassNotFoundException {
        var schemaCreator = findSchemaGenerator(object);
        var formattedSchema = formatter.formatSourceAndFixImports(schemaCreator.createSchema());
        writeFile(object, formattedSchema);
    }

    private SchemaCreator findSchemaGenerator(ProtobufObject<?> object) {
        if (object instanceof MessageStatement msg) {
            return new MessageSchemaCreator(msg, pack, true);
        }

        if(object instanceof EnumStatement enm){
            return new EnumSchemaCreator(enm, pack, true);
        }

        throw new IllegalArgumentException("Cannot find a schema generator for statement %s(%s)".formatted(object.getName(), object.getClass().getName()));
    }

    private void writeFile(ProtobufObject<?> object, String formattedSchema) throws IOException {
        Files.write(Path.of(directory.getPath(), "/%s.java".formatted(object.getName())), formattedSchema.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
