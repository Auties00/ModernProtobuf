package it.auties.protobuf.parser.type;

/**
 * Represents a numeric range in a Protocol Buffer definition.
 * <p>
 * Ranges are used in Protocol Buffer {@code reserved} and {@code extensions} statements to specify
 * a contiguous block of field numbers. A range can be either bounded (with both minimum and maximum values)
 * or lower-bounded (with only a minimum value, extending to the maximum allowed field number).
 * </p>
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * message Example {
 *   reserved 2, 15, 9 to 11;  // Bounded ranges: 9-11
 *   reserved 40 to max;       // Lower-bounded range: 40 to maximum
 *   extensions 100 to 200;    // Bounded extensions range
 * }
 * }</pre>
 *
 * @see Bounded
 * @see LowerBounded
 */
public sealed interface ProtobufRange {
    /**
     * Returns the minimum (starting) value of this range.
     *
     * @return the minimum value of the range
     */
    ProtobufInteger min();

    /**
     * Represents a bounded numeric range with both minimum and maximum values.
     * <p>
     * Bounded ranges are specified using the syntax {@code min to max} in Protocol Buffer definitions,
     * where both {@code min} and {@code max} are explicit integer values.
     * </p>
     *
     * @param min the minimum (starting) value of the range, inclusive
     * @param max the maximum (ending) value of the range, inclusive
     */
    record Bounded(ProtobufInteger min, ProtobufInteger max) implements ProtobufRange {

    }

    /**
     * Represents a lower-bounded numeric range with only a minimum value.
     * <p>
     * Lower-bounded ranges are specified using the syntax {@code min to max} where {@code max}
     * is the {@code max} keyword in Protocol Buffer definitions. This type of range extends from
     * the minimum value to the maximum allowed field number.
     * </p>
     *
     * @param min the minimum (starting) value of the range, inclusive
     */
    record LowerBounded(ProtobufInteger min) implements ProtobufRange {

    }
}
