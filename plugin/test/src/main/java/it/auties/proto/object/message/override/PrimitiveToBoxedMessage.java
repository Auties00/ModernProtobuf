package it.auties.proto.object.message.override;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;

public record PrimitiveToBoxedMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.UINT32, overrideType = Integer.class)
        int value
) implements ProtobufMessage {

}
