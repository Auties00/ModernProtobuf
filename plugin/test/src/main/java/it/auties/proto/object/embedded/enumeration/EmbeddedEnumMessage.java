package it.auties.proto.object.embedded.enumeration;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;

public record EmbeddedEnumMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.OBJECT)
        EnumWrapperMessage content
) implements ProtobufMessage {

}
