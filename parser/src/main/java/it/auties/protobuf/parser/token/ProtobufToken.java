package it.auties.protobuf.parser.token;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.parser.type.ProtobufFloatingPoint;
import it.auties.protobuf.parser.type.ProtobufNumber;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

public sealed interface ProtobufToken {
    record Literal(String value, char delimiter) implements ProtobufToken {
        public Literal {
            Objects.requireNonNull(value, "value cannot be null");
        }

        @Override
        public String toString() {
            return delimiter + value + delimiter;
        }
    }

    record Number(ProtobufNumber value) implements ProtobufToken {
        public Number {
            Objects.requireNonNull(value, "value cannot be null");
        }

       @Override
        public String toString() {
            return value.toString();
        }
    }

    record Boolean(boolean value) implements ProtobufToken {
        @Override
        public String toString() {
            return value ? "true" : "false";
        }
    }

    record Raw(String value) implements ProtobufToken {
        public Raw {
            Objects.requireNonNull(value, "value cannot be null");
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
