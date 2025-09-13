package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to static methods,
 * in a type annotated with {@link ProtobufMixin}
 * or that should be interpreted as a {@link ProtobufMessage}.
 * <h2>Usage Example:</h2>
 * <h3>In a {@link ProtobufMessage}:</h3>
 * <h4>From a Protobuf-compatible type:</h4>
 * <pre>{@code
 * record BirthdayDate(int day, int month, int year) {
 *     @ProtobufDeserializer
 *     static of(String date) {
 *         if (date == null) {
 *             return null;
 *         }
 *
 *         var dateParts = date.split("/", 3);
 *         if (dateParts.length != 3) {
 *             return null;
 *         }
 *
 *         try {
 *             var day = Integer.parseUnsignedInt(dateParts[0]);
 *             var month = Integer.parseUnsignedInt(dateParts[1]);
 *             var year = Integer.parseUnsignedInt(dateParts[2]);
 *             return new BirthdayDate(day, month, year);
 *         } catch (NumberFormatException exception) {
 *             return null;
 *         }
 *     }
 * }
 * }</pre>
 * <h4>From a Protobuf stream:</h4>
 * <pre>{@code
 * record BirthdayDate(int day, int month, int year) {
 *     @ProtobufDeserializer
 *     static of(ProtobufInputStream stream) {
 *         // index/wireType can be accessed from stream
 *         var dateParts = stream.readString().split("/", 3);
 *         if (dateParts.length != 3) {
 *             return null;
 *         }
 *
 *         try {
 *             var day = Integer.parseUnsignedInt(dateParts[0]);
 *             var month = Integer.parseUnsignedInt(dateParts[1]);
 *             var year = Integer.parseUnsignedInt(dateParts[2]);
 *             return new BirthdayDate(day, month, year);
 *         } catch (NumberFormatException exception) {
 *             return null;
 *         }
 *     }
 * }
 * }</pre>
 * <h3>In a {@link ProtobufMixin}:</h3>
 * <h4>From a Protobuf-compatible type:</h4>
 * <pre>{@code
 * @ProtobufMixin
 * public final class ProtobufAtomicMixin {
 *     @ProtobufDeserializer
 *     public static AtomicInteger ofAtomic(Integer value) {
 *         return value == null ? new AtomicInteger() : new AtomicInteger(value);
 *     }
 * }
 *}</pre>
 * <h4>From a Protobuf stream:</h4>
 * <pre>{@code
 * @ProtobufMixin
 * public final class ProtobufAtomicMixin {
 *     @ProtobufDeserializer
 *     public static AtomicInteger ofAtomic(ProtobufInputStream stream) {
 *         // index/wireType can be accessed from stream
 *         return value == null ? new AtomicInteger() : new AtomicInteger(stream.readInt32());
 *     }
 * }
 *}</pre>
 **/
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufDeserializer {
    /**
     * Provides an optional warning message that should be printed by the compiler when this deserializer is used.
     * By default, no warning message is specified.
     *
     * @return a string containing the warning message; if not specified, an empty string is returned
     */
    String warning() default "";
}
