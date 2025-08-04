package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to non-static methods,
 * in a type annotated with {@link ProtobufMixin}
 * or that should be interpreted as a {@link ProtobufMessage}.
 * <h2>Usage Example:</h2>
 * <h3>In a type interpreted as a {@link ProtobufMessage}:</h3>
 * <pre>{@code
 * record BirthdayDate(int day, int month, int year) {
 *     @ProtobufSerializer
 *     public String formatted() {
 *         return "%s/%s/%s".formatted(day, month, year);
 *     }
 * }
 * }</pre>
 * <h3>In a {@link ProtobufMixin}:</h3>
 * <pre>{@code
 * @ProtobufMixin
 * public final class ProtobufAtomicMixin {
 *     @ProtobufSerializer
 *     public static int toInt(AtomicInteger value) {
 *         return value.get();
 *     }
 * }
 *}</pre>
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
}
