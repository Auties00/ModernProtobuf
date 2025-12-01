package it.auties.protobuf.parser.type;

import it.auties.protobuf.annotation.ProtobufEnum;

import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Represents a numeric value in a Protocol Buffer definition.
 * <p>
 * This sealed interface represents arbitrary-precision numeric values that appear in Protocol Buffer
 * definitions. Numbers can be either integers ({@link ProtobufInteger}) or floating-point values
 * ({@link ProtobufFloatingPoint}), and support arbitrary precision to accurately represent
 * numeric literals without loss of precision during parsing.
 * </p>
 * <p>
 * Protocol Buffer numbers are used in various contexts including field indexes, enum values,
 * default values, and option values. This interface provides methods to convert numbers to
 * field indexes and enum constants with appropriate range validation.
 * </p>
 *
 * @see ProtobufInteger
 * @see ProtobufFloatingPoint
 */
public sealed interface ProtobufNumber
        extends Comparable<ProtobufNumber>
        permits ProtobufInteger, ProtobufFloatingPoint {
    /**
     * Attempts to convert this number to a valid Protocol Buffer field index.
     * <p>
     * Field indexes must be in the range [{@link it.auties.protobuf.annotation.ProtobufProperty#MIN_INDEX},
     * {@link it.auties.protobuf.annotation.ProtobufProperty#MAX_INDEX}]. If this number is outside
     * this range or cannot be represented as a long integer, an empty optional is returned.
     * </p>
     *
     * @return an {@link OptionalLong} containing the field index if valid, or empty otherwise
     */
    OptionalLong toFieldIndex();

    /**
     * Attempts to convert this number to a valid Protocol Buffer enum constant value.
     * <p>
     * Enum constant values must be in the range [{@link ProtobufEnum.Constant#MIN_INDEX},
     * {@link ProtobufEnum.Constant#MAX_INDEX}]. If this number is outside
     * this range or cannot be represented as an integer, an empty optional is returned.
     * </p>
     *
     * @return an {@link OptionalInt} containing the enum constant value if valid, or empty otherwise
     */
    OptionalInt toEnumConstant();
}
