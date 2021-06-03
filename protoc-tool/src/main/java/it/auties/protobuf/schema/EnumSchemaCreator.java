package it.auties.protobuf.schema;

import groovy.text.GStringTemplateEngine;
import it.auties.protobuf.ast.EnumStatement;
import it.auties.protobuf.utils.ProtobufUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public record EnumSchemaCreator(EnumStatement enumStatement, String pack, boolean imports) implements SchemaCreator{
    private static final String GENERATOR = ProtobufUtils.readGenerator("EnumTemplate");
    @Override
    public String createSchema() throws IOException, ClassNotFoundException {
        return new GStringTemplateEngine()
                .createTemplate(GENERATOR)
                .make(Map.of("enm", enumStatement, "pack", pack, "imports", imports))
                .writeTo(new StringWriter())
                .toString();
    }
}
