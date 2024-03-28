package it.auties.proto.object.message.modifier;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import static it.auties.protobuf.model.ProtobufType.STRING;

public record RequiredMessage(
        @ProtobufProperty(index = 1, type = STRING, required = true)
        String required,
        @ProtobufProperty(index = 2, type = STRING) String optional
) implements ProtobufMessage {

}
