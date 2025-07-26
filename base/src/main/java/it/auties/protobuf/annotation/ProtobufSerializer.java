package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to non-static methods,
 * in a type annotated with {@link ProtobufMixin}
 * or that should be interpreted as a {@link ProtobufMessage}.
 * <p>
 * Here is an example of how it can be used in a type that should be interpreted as a {@link ProtobufMessage}:
 * {@snippet :
 * record BirthdayDate(int day, int month, int year) {
 *     @ProtobufSerializer
 *     public String formatted() {
 *         return "%s/%s/%s".formatted(day, month, year);
 *     }
 * }
 * }
 * <p>
 * Here is an example of how it can be used in a {@link ProtobufMixin}:
 * {@snippet :
 * @ProtobufMixin
 * public final class ProtobufAtomicMixin {
 *     @ProtobufSerializer
 *     public static int toInt(AtomicInteger value) {
 *         return value.get();
 *     }
 * }
 * }
 */
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
