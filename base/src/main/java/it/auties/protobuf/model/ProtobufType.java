package it.auties.protobuf.model;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Enumeration representing all supported Protocol Buffer property types.
 * <p>
 * This enum defines the complete set of Protocol Buffer types as specified in the Protocol Buffer Language Guide.
 * Each type maintains information about its serialized representation, deserialized form,
 * and whether it supports packed encoding.
 * </p>
 *
 * <h2>Packed Encoding:</h2>
 * <p>
 * Numeric scalar types can be packed for more efficient encoding of repeated fields.
 * The {@link #isPackable()} method indicates whether a type supports this optimization.
 * </p>
 *
 * @see <a href="https://developers.google.com/protocol-buffers/docs/proto3#scalar">Protocol Buffer Scalar Types</a>
 */
public enum ProtobufType {
    /**
     * Represents an unknown or unrecognized type.
     */
    UNKNOWN(null, null, false),

    /**
     * Protocol Buffer message type.
     * Messages are types that can contain multiple fields and nested structures.
     * Serialized as byte arrays containing the encoded message data.
     */
    MESSAGE(byte[].class, byte[].class, false),

    /**
     * Protocol Buffer enum type.
     * Enums are represented as 32-bit integers with named constants.
     * The first enum value must be zero for proto3 compatibility.
     */
    ENUM(Integer.class, Integer.class, false),

    /**
     * Protocol Buffer group type (deprecated).
     * Groups are a legacy feature from proto2 that should not be used in new code.
     * They are similar to messages but use different wire encoding.
     * <p>
     * <strong>Deprecated:</strong> Groups are deprecated in Protocol Buffers and should not be used.
     * This type is only maintained for backward compatibility with legacy proto2 files.
     * Use nested messages instead.
     * </p>
     */
    GROUP(byte[].class, byte[].class, false),

    /**
     * Protocol Buffer map type.
     * Maps represent key-value pairs and are implemented as repeated message fields
     * with special semantics for key uniqueness.
     */
    MAP(Map.class, Map.class, false),

    /**
     * 32-bit floating point number.
     * Uses IEEE 754 single-precision format.
     * Always 4 bytes when encoded with a fixed32 wire type.
     */
    FLOAT(float.class, Float.class, true),

    /**
     * 64-bit floating point number.
     * Uses IEEE 754 double-precision format.
     * Always 8 bytes when encoded with a fixed64 wire type.
     */
    DOUBLE(double.class, Double.class, true),

    /**
     * Boolean value.
     * Encoded as a varint where 0 represents false and 1 represents true.
     * Can be packed for repeated fields.
     */
    BOOL(boolean.class, Boolean.class, true),

    /**
     * UTF-8 encoded string.
     * Must contain valid UTF-8 sequences.
     * Stored as length-delimited data with the string bytes.
     */
    STRING(ProtobufString.Lazy.class, ProtobufString.class, false),

    /**
     * Arbitrary sequence of bytes.
     * No encoding restrictions - can contain any binary data.
     * Stored as length-delimited data.
     */
    BYTES(ByteBuffer.class, ByteBuffer.class, false),

    /**
     * 32-bit signed integer using variable-length encoding.
     * Uses varint encoding which is efficient for small positive numbers
     * but inefficient for negative numbers (always uses 10 bytes).
     * Range: -2^31 to 2^31-1
     */
    INT32(int.class, Integer.class, true),

    /**
     * 32-bit signed integer using zigzag encoding.
     * More efficient than int32 for negative numbers as it maps
     * signed integers to unsigned integers: -1 becomes 1, -2 becomes 3, etc.
     * Range: -2^31 to 2^31-1
     */
    SINT32(int.class, Integer.class, true),

    /**
     * 32-bit unsigned integer using variable-length encoding.
     * Uses varint encoding, efficient for small numbers.
     * Range: 0 to 2^32-1
     */
    UINT32(int.class, Integer.class, true),

    /**
     * 32-bit unsigned integer using fixed-length encoding.
     * Always encoded as exactly 4 bytes in little-endian format.
     * More efficient than uint32 for large numbers (> 2^28).
     * Range: 0 to 2^32-1
     */
    FIXED32(int.class, Integer.class, true),

    /**
     * 32-bit signed integer using fixed-length encoding.
     * Always encoded as exactly 4 bytes in little-endian format.
     * More efficient than int32 for large numbers.
     * Range: -2^31 to 2^31-1
     */
    SFIXED32(int.class, Integer.class, true),

    /**
     * 64-bit signed integer using variable-length encoding.
     * Uses varint encoding which is efficient for small positive numbers
     * but inefficient for negative numbers (always uses 10 bytes).
     * Range: -2^63 to 2^63-1
     */
    INT64(long.class, Long.class, true),

    /**
     * 64-bit signed integer using zigzag encoding.
     * More efficient than int64 for negative numbers.
     * Range: -2^63 to 2^63-1
     */
    SINT64(long.class, Long.class, true),

    /**
     * 64-bit unsigned integer using variable-length encoding.
     * Uses varint encoding, efficient for small numbers.
     * Range: 0 to 2^64-1
     */
    UINT64(long.class, Long.class, true),

    /**
     * 64-bit unsigned integer using fixed-length encoding.
     * Always encoded as exactly 8 bytes in little-endian format.
     * More efficient than uint64 for large numbers (> 2^56).
     * Range: 0 to 2^64-1
     */
    FIXED64(long.class, Long.class, true),

    /**
     * 64-bit signed integer using fixed-length encoding.
     * Always encoded as exactly 8 bytes in little-endian format.
     * More efficient than int64 for large numbers.
     * Range: -2^63 to 2^63-1
     */
    SFIXED64(long.class, Long.class, true);

    private final Class<?> serializedType;
    private final Class<?> deserializableType;
    private final boolean packable;

    /**
     * Constructs a ProtobufType with the specified characteristics.
     *
     * @param serializedType the Java class used when serializing this type
     * @param deserializableType the Java class used when deserializing this type
     * @param packable whether this type supports packed encoding for repeated fields
     */
    ProtobufType(Class<?> serializedType, Class<?> deserializableType, boolean packable) {
        this.serializedType = serializedType;
        this.deserializableType = deserializableType;
        this.packable = packable;
    }

    /**
     * Resolves a Protocol Primitive Buffer type by name.
     * <p>
     * The lookup is case-sensitive and returns {@link #UNKNOWN} for unrecognized names.
     * </p>
     *
     * @param name the type name to resolve (case-insensitive), may be null
     * @return the corresponding primitive ProtobufType, or {@link #UNKNOWN} if the name is null or unrecognized
     */
    public static ProtobufType ofPrimitive(String name) {
        return switch (name) {
          case "string" -> STRING;
          case "bytes" -> BYTES;
          case "bool" -> BOOL;
          case "float" -> FLOAT;
          case "double" -> DOUBLE;
          case "int32" -> INT32;
          case "int64" -> INT64;
          case "uint32" -> UINT32;
          case "uint64" -> UINT64;
          case "sint32" -> SINT32;
          case "sint64" -> SINT64;
          case "fixed32" -> FIXED32;
          case "fixed64" -> FIXED64;
          case "sfixed32" -> SFIXED32;
          case "sfixed64" -> SFIXED64;
          case null, default -> UNKNOWN;
        };
    }

    /**
     * Returns the Java class used when serializing values of this type.
     * <p>
     * This is the class that should be used when preparing data for protocol buffer serialization.
     * For example, {@code INT32} returns {@code int.class} for primitive serialization.
     * </p>
     *
     * @return the serialization class for this type
     * @throws UnsupportedOperationException if this type is {@link #UNKNOWN}
     */
    public Class<?> serializedType() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return serializedType;
    }

    /**
     * Returns the Java class used when deserializing values of this type.
     * <p>
     * This is the class that results from protocol buffer deserialization.
     * For example, {@code INT32} returns {@code Integer.class} for boxed deserialization.
     * </p>
     *
     * @return the deserialization class for this type
     * @throws UnsupportedOperationException if this type is {@link #UNKNOWN}
     */
    public Class<?> deserializableType() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return deserializableType;
    }

    /**
     * Indicates whether this type supports packed encoding.
     * <p>
     * Packed encoding is a space-efficient way to encode repeated fields of numeric scalar types.
     * Only primitive numeric types (integers, floats, doubles, and booleans) support packing.
     * </p>
     *
     * @return true if this type can be packed in repeated fields, false otherwise
     *
     * @see <a href="https://developers.google.com/protocol-buffers/docs/encoding#packed">Protocol Buffer Packed Encoding</a>
     */
    public boolean isPackable() {
        return this.packable;
    }

    /**
     * Determines if this type represents a primitive Protocol Buffer type.
     *
     * @return true if this is a primitive type (scalar), false otherwise
     */
    public boolean isPrimitive() {
        return switch (this) {
            case UNKNOWN, MESSAGE, ENUM, GROUP, MAP -> false;
            case FLOAT, DOUBLE, BOOL, STRING, BYTES, INT32, SINT32, UINT32,
                 FIXED32, SFIXED32, INT64, SINT64, SFIXED64, UINT64, FIXED64 -> true;
        };
    }
}