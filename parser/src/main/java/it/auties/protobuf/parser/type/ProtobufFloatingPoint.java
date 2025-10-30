package it.auties.protobuf.parser.type;

import java.math.BigDecimal;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Represents a floating-point value in a Protocol Buffer definition.
 * <p>
 * This sealed interface represents floating-point numeric values that appear in Protocol Buffer
 * definitions, including:
 * </p>
 * <ul>
 *   <li>Finite decimal numbers: {@code 3.14}, {@code -0.5}, {@code 2.5e10}, {@code 1.0e-5}</li>
 *   <li>Positive infinity: {@code inf}</li>
 *   <li>Negative infinity: {@code -inf}</li>
 *   <li>Not a Number: {@code nan}</li>
 * </ul>
 * <p>
 * Floating-point values cannot be used as field indexes or enum constants, so the
 * {@link #toFieldIndex()} and {@link #toEnumConstant()} methods always return empty optionals.
 * </p>
 * <p>
 * The interface has three implementations:
 * </p>
 * <ul>
 *   <li>{@link Finite} - Represents finite floating-point values with arbitrary precision</li>
 *   <li>{@link Infinity} - Represents positive or negative infinity</li>
 *   <li>{@link NaN} - Represents the special "Not a Number" value</li>
 * </ul>
 *
 * @see Finite
 * @see Infinity
 * @see NaN
 */
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

    /**
     * Represents a finite floating-point value with arbitrary precision.
     * <p>
     * Finite floating-point values include decimal numbers and scientific notation.
     * The value is stored as a {@link BigDecimal} to preserve the exact precision
     * of the literal as written in the Protocol Buffer definition.
     * </p>
     *
     * @param value the arbitrary-precision decimal value
     */
    record Finite(BigDecimal value) implements ProtobufFloatingPoint {

    }

    /**
     * Represents positive or negative infinity in a Protocol Buffer definition.
     * <p>
     * Infinity values are represented by the {@code inf} keyword for positive infinity
     * and {@code -inf} for negative infinity in Protocol Buffer files.
     * </p>
     *
     * @param signum the sign of the infinity value (positive or negative)
     */
    record Infinity(Signum signum) implements ProtobufFloatingPoint {
        /**
         * Represents the sign of an infinity value.
         */
        public enum Signum {
            /**
             * Represents positive infinity ({@code inf}).
             */
            POSITIVE,

            /**
             * Represents negative infinity ({@code -inf}).
             */
            NEGATIVE
        }
    }

    /**
     * Represents the special "Not a Number" (NaN) floating-point value.
     * <p>
     * NaN is represented by the {@code nan} keyword in Protocol Buffer files and represents
     * an undefined or unrepresentable floating-point value, similar to IEEE 754 NaN.
     * </p>
     */
    record NaN() implements ProtobufFloatingPoint {

    }
}
