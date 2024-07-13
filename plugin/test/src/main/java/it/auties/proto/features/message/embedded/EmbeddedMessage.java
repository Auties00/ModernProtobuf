package it.auties.proto.features.message.embedded;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record EmbeddedMessage(
        @ProtobufProperty(index = 3, type = STRING)
        String content
) {

}
