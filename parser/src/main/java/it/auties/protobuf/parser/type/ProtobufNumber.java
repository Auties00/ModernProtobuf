package it.auties.protobuf.parser.type;

import java.util.OptionalInt;
import java.util.OptionalLong;

public sealed interface ProtobufNumber
        extends Comparable<ProtobufNumber>
        permits ProtobufInteger, ProtobufFloatingPoint {
    OptionalLong toFieldIndex();
    OptionalInt toEnumConstant();
}
