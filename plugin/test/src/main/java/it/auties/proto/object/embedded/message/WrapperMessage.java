package it.auties.proto.object.embedded.message;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import static it.auties.protobuf.model.ProtobufType.OBJECT;

public record WrapperMessage(
        @ProtobufProperty(index = 1, type = OBJECT)
        EmbeddedMessage content
) implements ProtobufMessage {

}
