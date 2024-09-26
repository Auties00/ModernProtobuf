package it.auties.proto.breakMe;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage
public record OptionalPrimitive(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        Optional<String> textWithNoContextMessage
) {

}