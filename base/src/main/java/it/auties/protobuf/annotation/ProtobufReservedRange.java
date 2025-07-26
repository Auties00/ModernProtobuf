package it.auties.protobuf.annotation;

/**
 * This annotation represents a range of numeric values that are reserved and cannot be used
 * in the context of Protobuf definitions. A reserved range ensures compatibility
 * or avoids conflicts when defining numeric values in Protobuf.
 */
public @interface ProtobufReservedRange {
    /**
     * Returns the minimum value of the reserved numeric range, inclusive.
     *
     * @return the minimum value of the reserved numeric range
     */
    long min();

    /**
     * Returns the maximum value of the reserved numeric range, inclusive.
     *
     * @return the maximum value of the reserved numeric range
     */
    long max();
}