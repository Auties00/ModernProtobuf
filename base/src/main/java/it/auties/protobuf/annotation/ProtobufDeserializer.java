package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to static methods,
 * in a type annotated with {@link ProtobufMixin}
 * or that should be interpreted as a {@link ProtobufMessage}.
 * <p>
 * Here is an example of how it can be used in a type that should be interpreted as a {@link ProtobufMessage}:
 * {@snippet :
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
 * }
 * <p>
 * Here is an example of how it can be used in a {@link ProtobufMixin}:
 * {@snippet :
 * @ProtobufMixin
 * public final class ProtobufAtomicMixin {
 *     @ProtobufDeserializer
 *     public static AtomicInteger ofAtomic(Integer value) {
 *         return value == null ? new AtomicInteger() : new AtomicInteger(value);
 *     }
 * }
 *}
 */
@Target(ElementType.METHOD)
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
