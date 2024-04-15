package it.auties.proto.object.message.optional;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static it.auties.protobuf.model.ProtobufType.*;

public record OptionalMessage(
        @ProtobufProperty(index = 1, type = STRING)
        Optional<String> optionalString,
        @ProtobufProperty(index = 2, type = UINT32)
        OptionalInt optionalInt,
        @ProtobufProperty(index = 3, type = UINT64)
        OptionalLong optionalLong,
        @ProtobufProperty(index = 4, type = DOUBLE)
        OptionalDouble optionalDouble
) implements ProtobufMessage {

}
