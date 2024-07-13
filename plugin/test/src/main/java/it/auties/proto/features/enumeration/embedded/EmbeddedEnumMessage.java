package it.auties.proto.features.enumeration.embedded;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record EmbeddedEnumMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.OBJECT)
        EnumWrapperMessage content
) {

}
