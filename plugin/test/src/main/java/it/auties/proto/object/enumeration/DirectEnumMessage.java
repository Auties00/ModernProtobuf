package it.auties.proto.object.enumeration;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;

public record DirectEnumMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.OBJECT)
        EnumType content,

        @ProtobufProperty(index = 2, type = ProtobufType.OBJECT)
        EnumType content1
) implements ProtobufMessage {

}
