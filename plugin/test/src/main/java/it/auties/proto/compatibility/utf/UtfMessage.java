package it.auties.proto.compatibility.utf;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record UtfMessage(
        @ProtobufProperty(index = 1, type = STRING)
        String content
) {

}
