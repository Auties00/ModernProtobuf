package it.auties.protobuf.schema;

import groovy.text.GStringTemplateEngine;
import it.auties.protobuf.parser.model.OneOfStatement;
import it.auties.protobuf.utils.ProtobufUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public record OneOfSchemaCreator(OneOfStatement oneOfStatement, String pack, boolean imports) implements SchemaCreator{
    private static final String GENERATOR = ProtobufUtils.readGenerator("OneOfTemplate");

    @Override
    public String createSchema() throws IOException, ClassNotFoundException {
        return new GStringTemplateEngine()
                .createTemplate(GENERATOR)
                .make(Map.of("enm", oneOfStatement, "pack", pack, "imports", imports))
                .writeTo(new StringWriter())
                .toString();
    }
}
