package it.auties.proto.message.missing;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import static it.auties.protobuf.model.ProtobufType.STRING;

public record SerializableMessage(
        @ProtobufProperty(index = 1, type = STRING)
        String content,

        @ProtobufProperty(index = 2, type = STRING)
        String content1,

        @ProtobufProperty(index = 3, type = STRING)
        String content2,

        @ProtobufProperty(index = 4, type = STRING)
        String content3
) implements ProtobufMessage {

}
