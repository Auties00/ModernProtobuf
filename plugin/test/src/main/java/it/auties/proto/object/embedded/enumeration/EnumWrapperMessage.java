package it.auties.proto.object.embedded.enumeration;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;

public record EnumWrapperMessage(
        @ProtobufProperty(index = 3, type = ProtobufType.OBJECT)
        EnumType type
) implements ProtobufMessage {

}
