package it.auties.proto.object.message.missing;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record SerializableMessage(
        @ProtobufProperty(index = 1, type = STRING)
        String content,

        @ProtobufProperty(index = 2, type = STRING)
        String content1,

        @ProtobufProperty(index = 3, type = STRING)
        String content2,

        @ProtobufProperty(index = 4, type = STRING)
        String content3
) {

}
