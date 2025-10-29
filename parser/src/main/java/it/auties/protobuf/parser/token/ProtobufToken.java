package it.auties.protobuf.parser.token;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;

public sealed interface ProtobufToken {
    record Literal(String value) implements ProtobufToken {

    }

    sealed interface Number extends ProtobufToken {
        boolean isValidPropertyIndex();

        boolean isValidEnumConstantIndex();

        record Integer(long value) implements Number {
            @Override
            public boolean isValidPropertyIndex() {
                return value >= ProtobufProperty.MIN_INDEX && value <= ProtobufProperty.MAX_INDEX;
            }

            @Override
            public boolean isValidEnumConstantIndex() {
                return value >= ProtobufEnumIndex.MIN_VALUE && value <= ProtobufEnumIndex.MAX_VALUE;
            }
        }

        record FloatingPoint(double value) implements Number {
            @Override
            public boolean isValidPropertyIndex() {
                return false;
            }

            @Override
            public boolean isValidEnumConstantIndex() {
                return false;
            }
        }
    }

    record Boolean(boolean value) implements ProtobufToken {

    }

    record Raw(String value) implements ProtobufToken {

    }
}
