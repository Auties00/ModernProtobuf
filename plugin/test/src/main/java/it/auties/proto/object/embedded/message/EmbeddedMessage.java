package it.auties.proto.object.embedded.message;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import static it.auties.protobuf.model.ProtobufType.STRING;

public record EmbeddedMessage(
        @ProtobufProperty(index = 3, type = STRING)
        String content
) implements ProtobufMessage {

}
