package it.auties.proto.object.defaultValue;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;

public record WrapperMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.OBJECT)
        OptionalMessage optionalMessage
) implements ProtobufMessage {

}
