package it.auties.protobuf.schema;

import org.simart.writeonce.common.GeneratorException;

public interface SchemaCreator {
    String createSchema() throws GeneratorException;
}
