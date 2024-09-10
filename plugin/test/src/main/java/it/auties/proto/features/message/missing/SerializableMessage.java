package it.auties.proto.features.message.missing;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record SerializableMessage(
        @ProtobufProperty(index = 1, type = STRING)
        ProtobufString content,
        @ProtobufProperty(index = 2, type = STRING)
        ProtobufString content1,
        @ProtobufProperty(index = 3, type = STRING)
        ProtobufString content2,
        @ProtobufProperty(index = 4, type = STRING)
        ProtobufString content3
) {

}
