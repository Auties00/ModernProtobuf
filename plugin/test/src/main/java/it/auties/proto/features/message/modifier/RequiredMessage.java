package it.auties.proto.features.message.modifier;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record RequiredMessage(
        @ProtobufProperty(index = 1, type = STRING, required = true)
        ProtobufString required,
        @ProtobufProperty(index = 2, type = STRING)
        ProtobufString optional
) {

}
