package it.auties.protobuf.annotation;

import it.auties.protobuf.stream.ProtobufOutputStream;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to non-static methods,
 * in a type annotated with {@link ProtobufMixin}
 * or that should be interpreted as a {@link ProtobufMessage},
 * to specify how said type should be serialized.
 * It's possible to return a Protobuf-compatible value, like a primitive or a message,
 * or to write directly to the output stream.
 * The first approach is recommended for most scenarios as it's easier to implement,
 * while the second can be useful when trying to minimize allocations.
 * <h2>Usage Example:</h2>
 * <h3>In a type interpreted as a {@link ProtobufMessage}:</h3>
 * <h4>To a Protobuf-compatible type:</h4>
 * <pre>{@code
 * record BirthdayDate(int day, int month, int year) {
 *     @ProtobufSerializer
 *     public String formatted() {
 *         return "%s/%s/%s".formatted(day, month, year);
 *     }
 *
 *     // A method annotated with @ProtobufSerializer.Length will be ignored if the formatted serializer is used
 * }
 * }</pre>
 * <h3>In a {@link ProtobufMixin}:</h3>
 * <h4>To a Protobuf-compatible type:</h4>
 * <pre>{@code
 * @ProtobufMixin
 * public final class ProtobufAtomicMixin {
 *     @ProtobufSerializer
 *     public static int toInt(AtomicInteger value) {
 *         return value.get();
 *     }
 *
 *     // A method annotated with @ProtobufSerializer.Length will be ignored if the toInt serializer is used
 * }
 *}</pre>
 *
 * For examples on how to write directly to a {@link ProtobufOutputStream}, check {@link Length}
 **/
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufSerializer {
    /**
     * Provides an optional warning message that should be printed by the compiler when this serializer is used.
     * By default, no warning message is specified.
     *
     * @return a string containing the warning message; if not specified, an empty string is returned
     */
    String warning() default "";

    /**
     * This annotation can be applied to non-static methods,
     * in a type annotated with {@link ProtobufMixin}
     * or that should be interpreted as a {@link ProtobufMessage}.
     * <h2>Usage Example:</h2>
     * <pre>{@code
     * public final class SignalPublicKey {
     *     private static final byte KEY_TYPE = 5;
     *     private static final int KEY_LENGTH = 32;
     *     private static final int KEY_WITH_TYPE_LENGTH = 33;
     *     private final byte[] point;
     *
     *     public static byte type() {
     *         return KEY_TYPE;
     *     }
     *
     *     public static int length() {
     *         return KEY_LENGTH;
     *     }
     *
     *     @ProtobufDeserializer
     *     public SignalPublicKey(byte[] point) {
     *         Objects.requireNonNull(point, "key cannot be null");
     *         this.point = switch (point.length) {
     *             case KEY_LENGTH -> point;
     *             case KEY_WITH_TYPE_LENGTH -> {
     *                 if (point[0] != KEY_TYPE) {
     *                     throw new IllegalArgumentException("Invalid key type");
     *                 }
     *
     *                 yield Arrays.copyOfRange(point, 1, KEY_WITH_TYPE_LENGTH);
     *             }
     *             default -> throw new IllegalArgumentException("Invalid key length: " + point.length);
     *         };
     *     }
     *
     *     public byte[] encodedPoint() {
     *         return point;
     *     }
     *
     *     @ProtobufSerializer
     *     void writeToProto(ProtobufOutputStream<?> stream, long fieldIndex) {
     *         stream.writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
     *         stream.writeFieldValue(type);
     *         stream.writeFieldValue(key);
     *     }
     *
     *     @ProtobufSerializer.Length
     *     int protoLength(long fieldIndex) {
     *         return ProtobufOutputStream.getFieldSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
     *             + ProtobufOutputStream.getBytesSize(type)
     *             + ProtobufOutputStream.getBytesSize(bytes);
     *     }
     * }
     *}</pre>
     **/
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Length {

    }
}
