package it.auties.proto.features.message.missing;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record DeserializableMessage(
        @ProtobufProperty(index = 3, type = STRING)
        String content2
) {

}
