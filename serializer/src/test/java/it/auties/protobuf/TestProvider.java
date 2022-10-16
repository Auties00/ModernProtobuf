package it.auties.protobuf;

import it.auties.protobuf.serializer.jackson.ProtobufMapper;

public interface TestProvider {
    ProtobufMapper JACKSON = new ProtobufMapper();
}
