package it.auties.proto.features.message.unknownFields;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record ExtendedMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        ProtobufString value,
        @ProtobufProperty(index = 2, type = ProtobufType.INT32)
        int extended
) {

}
