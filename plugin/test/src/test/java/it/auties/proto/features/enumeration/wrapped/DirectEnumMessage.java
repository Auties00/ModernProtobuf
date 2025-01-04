package it.auties.proto.features.enumeration.wrapped;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record DirectEnumMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        EnumType content,
        @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
        EnumType content1
) {

}
