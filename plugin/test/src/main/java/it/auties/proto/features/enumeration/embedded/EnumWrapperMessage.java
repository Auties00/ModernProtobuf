package it.auties.proto.features.enumeration.embedded;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record EnumWrapperMessage(
        @ProtobufProperty(index = 3, type = ProtobufType.OBJECT)
        EnumType type
) {

}
