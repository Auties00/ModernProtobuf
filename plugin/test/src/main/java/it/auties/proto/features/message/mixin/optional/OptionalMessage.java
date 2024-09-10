package it.auties.proto.features.message.mixin.optional;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static it.auties.protobuf.model.ProtobufType.*;

@ProtobufMessage
public record OptionalMessage(
        @ProtobufProperty(index = 1, type = STRING)
        Optional<ProtobufString> optionalString,
        @ProtobufProperty(index = 2, type = UINT32)
        OptionalInt optionalInt,
        @ProtobufProperty(index = 3, type = UINT64)
        OptionalLong optionalLong,
        @ProtobufProperty(index = 4, type = DOUBLE)
        OptionalDouble optionalDouble,
        @ProtobufProperty(index = 5, type = OBJECT)
        Optional<OptionalMessage> optionalMessage
) {

}
