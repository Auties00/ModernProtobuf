package it.auties.protobuf.tool.schema;

import groovy.text.GStringTemplateEngine;
import it.auties.protobuf.parser.statement.MessageStatement;
import it.auties.protobuf.tool.util.ProtobufUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public record MessageSchemaCreator(MessageStatement message, String pack, boolean imports) implements SchemaCreator {
    private static final String GENERATOR = ProtobufUtils.readGenerator("MessageTemplate");

    @Override
    public String createSchema() throws IOException, ClassNotFoundException {
        return new GStringTemplateEngine()
                .createTemplate(GENERATOR)
                .make(Map.of("message", message, "pack", pack, "imports", imports))
                .writeTo(new StringWriter())
                .toString();
    }
}
