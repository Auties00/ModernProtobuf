package it.auties.proto.features.message.modifier;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record RequiredMessage(
        @ProtobufProperty(index = 1, type = STRING, required = true)
        String required,
        @ProtobufProperty(index = 2, type = STRING) String optional
) {

}
