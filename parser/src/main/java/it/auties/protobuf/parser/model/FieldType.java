package it.auties.protobuf.parser.model;

import java.util.Arrays;
import java.util.Optional;

public enum FieldType {
    MESSAGE,
    FLOAT,
    DOUBLE,
    BOOL,
    STRING,
    BYTES,
    INT32,
    SINT32,
    UINT32,
    FIXED32,
    SFIXED32,
    INT64,
    SINT64,
    UINT64,
    FIXED64,
    SFIXED64;

    public static Optional<FieldType> forName(String name) {
        return Arrays.stream(values())
                .filter(entry -> entry.name().equalsIgnoreCase(name))
                .findFirst();
    }
}
