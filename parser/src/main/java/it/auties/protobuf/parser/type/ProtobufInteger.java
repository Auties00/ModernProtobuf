package it.auties.protobuf.parser.type;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;

import java.math.BigInteger;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Represents an arbitrary-precision integer value in a Protocol Buffer definition.
 * <p>
 * This record encapsulates integer literals parsed from Protocol Buffer files, supporting
 * various integer formats:
 * </p>
 * <ul>
 *   <li>Decimal integers: {@code 42}, {@code -123}, {@code 0}</li>
 *   <li>Hexadecimal integers: {@code 0x2A}, {@code 0xFF}, {@code 0X10}</li>
 *   <li>Octal integers: {@code 052}, {@code 0177}, {@code 01}</li>
 * </ul>
 * <p>
 * The integer value is stored as a {@link BigInteger} to support arbitrary precision,
 * ensuring that large integer literals in Protocol Buffer definitions are represented
 * accurately without overflow. Range validation is performed when converting to specific
 * Protocol Buffer contexts such as field indexes or enum constants.
 * </p>
 *
 * @param value the arbitrary-precision integer value
 */
public record ProtobufInteger(BigInteger value) implements ProtobufNumber {
    private static final BigInteger MIN_PROPERTY_INDEX = BigInteger.valueOf(ProtobufProperty.MIN_INDEX);
    private static final BigInteger MAX_PROPERTY_INDEX = BigInteger.valueOf(ProtobufProperty.MAX_INDEX);

    private static final BigInteger MIN_ENUM_CONSTANT_INDEX = BigInteger.valueOf(ProtobufEnumIndex.MIN_VALUE);
    private static final BigInteger MAX_ENUM_CONSTANT_INDEX = BigInteger.valueOf(ProtobufEnumIndex.MAX_VALUE);

    @Override
    public OptionalLong toFieldIndex() {
        return value.compareTo(MIN_PROPERTY_INDEX) >= 0 && value.compareTo(MAX_PROPERTY_INDEX) <= 0
                ? OptionalLong.of(value.longValueExact())
                : OptionalLong.empty();
    }

    @Override
    public OptionalInt toEnumConstant() {
        return value.compareTo(MIN_ENUM_CONSTANT_INDEX) >= 0 && value.compareTo(MAX_ENUM_CONSTANT_INDEX) <= 0
                ? OptionalInt.of(value.intValueExact())
                : OptionalInt.empty();
    }

    @Override
    public int compareTo(ProtobufNumber other) {
        return switch (other) {
            case ProtobufFloatingPoint otherFloatingPoint -> switch (otherFloatingPoint) {
                // TODO: Maybe optimize me
                case ProtobufFloatingPoint.Finite(var otherValue) -> value.compareTo(otherValue.toBigInteger());
                case ProtobufFloatingPoint.Infinity(var signum) -> switch (signum) {
                    case POSITIVE -> -1;
                    case NEGATIVE -> 1;
                };
                case ProtobufFloatingPoint.NaN _ -> 1;
            };
            case ProtobufInteger(var otherValue) -> value.compareTo(otherValue);
        };
    }
}
