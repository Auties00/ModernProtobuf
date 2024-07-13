package it.auties.proto.features.message.conversion;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record WrapperMessage(
        @ProtobufProperty(index = 1, type = STRING)
        Wrapper wrapper
) {

}
