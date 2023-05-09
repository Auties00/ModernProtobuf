package it.auties.protobuf.base;

import lombok.experimental.StandardException;

@StandardException
public class ProtobufSerializationException extends ProtobufException {
    public static ProtobufSerializationException missingMandatoryField(String name) {
        return new ProtobufSerializationException("A message didn't contain a mandatory field: %s".formatted(name));
    }
}
