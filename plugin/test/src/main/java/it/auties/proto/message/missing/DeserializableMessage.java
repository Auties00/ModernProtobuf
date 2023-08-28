package it.auties.proto.message.missing;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import static it.auties.protobuf.model.ProtobufType.STRING;

public record DeserializableMessage(
        @ProtobufProperty(index = 3, type = STRING)
        String content2
) implements ProtobufMessage {

}
