package it.auties.protobuf.schema;

import it.auties.protobuf.ast.MessageStatement;
import it.auties.protobuf.utils.ProtobufUtils;
import org.simart.writeonce.application.Generator;
import org.simart.writeonce.common.GeneratorException;

public record MessageSchemaCreator(MessageStatement message, String pack, boolean imports) implements SchemaCreator{
    private static final String GENERATOR = ProtobufUtils.readGenerator("MessageTemplate");
    @Override
    public String createSchema() throws GeneratorException {
        return Generator.create(GENERATOR)
                .bindValue("message", message)
                .bindValue("pack", pack)
                .bindValue("imports", imports)
                .generate();
    }
}
