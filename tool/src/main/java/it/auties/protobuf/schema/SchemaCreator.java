package it.auties.protobuf.schema;

import java.io.IOException;

public interface SchemaCreator {
    String createSchema() throws IOException, ClassNotFoundException;
}
