package it.auties.protobuf.schema;

import groovy.text.GStringTemplateEngine;
import it.auties.protobuf.ast.MessageStatement;
import it.auties.protobuf.utils.ProtobufUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public record MessageSchemaCreator(MessageStatement message, String pack, boolean imports) implements SchemaCreator{
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
