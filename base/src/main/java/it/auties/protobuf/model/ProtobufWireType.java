
package it.auties.protobuf.model;

/**
 * Defines the wire types used in Protocol Buffers encoding.
 * <p>
 * Wire types determine how data is encoded on the wire and are essential for 
 * properly parsing protobuf messages. Each field in a protobuf message is 
 * encoded with a tag that contains both the field number and the wire type.
 * </p>
 * <p>
 * The wire type tells the parser how to interpret the following bytes:
 * </p>
 * <ul>
 *   <li>VARINT: Variable-length integers (int32, int64, uint32, uint64, sint32, sint64, bool, enum)</li>
 *   <li>FIXED64: Fixed 64-bit values (fixed64, sfixed64, double)</li>
 *   <li>LENGTH_DELIMITED: Variable-length data (string, bytes, embedded messages, packed repeated fields)</li>
 *   <li>START_GROUP: Start of a group (deprecated)</li>
 *   <li>END_GROUP: End of a group (deprecated)</li>
 *   <li>FIXED32: Fixed 32-bit values (fixed32, sfixed32, float)</li>
 * </ul>
 *
 * @see <a href="https://developers.google.com/protocol-buffers/docs/encoding">Protocol Buffers Encoding</a>
 */
public final class ProtobufWireType {

    /**
     * Wire type for variable-length integers.
     * <p>
     * Used for encoding int32, int64, uint32, uint64, sint32, sint64, bool, and enum values.
     * Values are encoded using variable-length encoding where smaller numbers use fewer bytes.
     * </p>
     */
    public final static int WIRE_TYPE_VAR_INT = 0;

    /**
     * Wire type for fixed 64-bit values.
     * <p>
     * Used for encoding fixed64, sfixed64, and double values.
     * Always uses exactly 8 bytes in little-endian byte order.
     * </p>
     */
    public final static int WIRE_TYPE_FIXED64 = 1;

    /**
     * Wire type for length-delimited data.
     * <p>
     * Used for encoding string, bytes, embedded messages, and packed repeated fields.
     * The data is prefixed with a varint indicating the length of the following data.
     * </p>
     */
    public final static int WIRE_TYPE_LENGTH_DELIMITED = 2;

    /**
     * Wire type for the start of a group. (deprecated)
     * <p>
     * <strong>Deprecated:</strong> Groups are deprecated in Protocol Buffers and should not be used.
     * This wire type is only maintained for backward compatibility with legacy proto2 files.
     * Use nested messages instead.
     * </p>
     */
    public final static int WIRE_TYPE_START_OBJECT = 3;

    /**
     * Wire type for the end of a group.
     * <p>
     * <strong>Deprecated:</strong> Groups are deprecated in Protocol Buffers and should not be used.
     * This wire type is only maintained for backward compatibility with legacy proto2 files.
     * Use nested messages instead.
     * </p>
     */
    public final static int WIRE_TYPE_END_OBJECT = 4;

    /**
     * Wire type for fixed 32-bit values.
     * <p>
     * Used for encoding fixed32, sfixed32, and float values.
     * Always uses exactly 4 bytes in little-endian byte order.
     * </p>
     */
    public final static int WIRE_TYPE_FIXED32 = 5;

    /**
     * Creates a protobuf tag by combining a field number and wire type.
     * <p>
     * In Protocol Buffers, each field is encoded with a tag that combines the field number
     * and wire type into a single varint. The field number is stored in the upper bits
     * (bits 3 and up) while the wire type is stored in the lower 3 bits.
     * </p>
     * <p>
     * The formula is: {@code tag = (fieldNumber << 3) | wireType}
     * </p>
     *
     * @param fieldNumber the field number from the .proto file
     * @param wireType the wire type constant (one of the WIRE_TYPE_* constants in this class)
     * @return the encoded tag as an integer
     *
     * @see <a href="https://developers.google.com/protocol-buffers/docs/encoding#structure">Protocol Buffers Wire Format</a>
     */
    public static long makeTag(long fieldNumber, int wireType) {
        return (fieldNumber << 3) | wireType;
    }
}