package it.auties.proto.features.message.missing;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record DeserializableMessage(
        @ProtobufProperty(index = 3, type = STRING)
        ProtobufString content2
) {

}
