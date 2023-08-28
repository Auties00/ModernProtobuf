package it.auties.proto.compatibility.utf;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import static it.auties.protobuf.model.ProtobufType.STRING;

public record UtfMessage(
        @ProtobufProperty(index = 1, type = STRING)
        String content
) implements ProtobufMessage {

}
