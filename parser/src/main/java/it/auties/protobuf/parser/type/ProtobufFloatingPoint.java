package it.auties.protobuf.parser.type;

import java.math.BigDecimal;
import java.util.OptionalInt;
import java.util.OptionalLong;

public sealed interface ProtobufFloatingPoint extends ProtobufNumber {
    @Override
    default OptionalLong toFieldIndex() {
        return OptionalLong.empty();
    }

    @Override
    default OptionalInt toEnumConstant() {
        return OptionalInt.empty();
    }

    @Override
    default int compareTo(ProtobufNumber other) {
        return switch (other) {
            case ProtobufFloatingPoint otherFloating -> switch (this) {
                case Finite(var value) -> switch (otherFloating) {
                    case Finite(var otherValue) -> value.compareTo(otherValue);
                    case Infinity(var signum) -> switch (signum) {
                        case POSITIVE -> -1;
                        case NEGATIVE -> 1;
                    };
                    case NaN _ -> -1;
                };
                case Infinity(var signum) -> switch (otherFloating) {
                    case Finite _ -> switch (signum) {
                        case POSITIVE -> 1;
                        case NEGATIVE -> -1;
                    };
                    case Infinity(var otherSignum) -> signum.compareTo(otherSignum);
                    case NaN _ -> -1;
                };
                case NaN _ -> otherFloating instanceof NaN ? 0 : 1;
            };
            case ProtobufInteger(var otherValue) -> switch (this) {
                // TODO: Optimize me
                case Finite(var value) -> value.compareTo(new BigDecimal(otherValue));
                case Infinity(var signum) -> switch (signum) {
                    case POSITIVE -> 1;
                    case NEGATIVE -> -1;
                };
                case NaN _ -> 1;
            };
        };
    }

    record Finite(BigDecimal value) implements ProtobufFloatingPoint {

    }

    record Infinity(Signum signum) implements ProtobufFloatingPoint {
        public enum Signum {
            POSITIVE,
            NEGATIVE
        }
    }

    record NaN() implements ProtobufFloatingPoint {

    }
}
