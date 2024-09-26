package it.auties.proto.breakMe;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage
public record OptionalEnum(
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        Optional<Enum> textWithNoContextMessage
) {
        @ProtobufEnum
        public enum Enum {
                FIRST,
                SECOND,
                THIRD
        }
}