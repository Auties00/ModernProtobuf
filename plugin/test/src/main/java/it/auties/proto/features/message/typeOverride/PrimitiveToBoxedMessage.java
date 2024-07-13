package it.auties.proto.features.message.typeOverride;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record PrimitiveToBoxedMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.UINT32, overrideType = Integer.class)
        int value
) {

}
