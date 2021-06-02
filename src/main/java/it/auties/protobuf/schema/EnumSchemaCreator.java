package it.auties.protobuf.schema;

import it.auties.protobuf.ast.EnumStatement;
import it.auties.protobuf.ast.MessageStatement;
import it.auties.protobuf.utils.ProtobufUtils;
import org.simart.writeonce.application.Generator;
import org.simart.writeonce.common.GeneratorException;

public record EnumSchemaCreator(EnumStatement enumStatement, String pack, boolean imports) implements SchemaCreator{
    private static final String GENERATOR = ProtobufUtils.readGenerator("EnumTemplate");
    @Override
    public String createSchema() throws GeneratorException {
        var generator = Generator.create(GENERATOR);
        generator.bindValue("enm", enumStatement);
        generator.bindValue("pack", pack);
        generator.bindValue("imports", imports);
        return generator.generate();
    }
}
