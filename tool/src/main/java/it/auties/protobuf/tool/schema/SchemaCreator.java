package it.auties.protobuf.tool.schema;

import java.io.IOException;

public interface SchemaCreator {
    String createSchema() throws IOException, ClassNotFoundException;
}
