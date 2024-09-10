package it.auties.proto.features.message.embedded;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record EmbeddedMessage(
        @ProtobufProperty(index = 3, type = STRING)
        ProtobufString content
) {

}
