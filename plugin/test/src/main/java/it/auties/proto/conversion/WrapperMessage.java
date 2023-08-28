package it.auties.proto.conversion;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import static it.auties.protobuf.model.ProtobufType.STRING;

public record WrapperMessage(
        @ProtobufProperty(index = 1, type = STRING)
        Wrapper wrapper
) implements ProtobufMessage {

}
