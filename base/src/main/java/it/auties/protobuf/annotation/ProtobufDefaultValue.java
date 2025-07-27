package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to static methods,
 * in a type annotated with {@link ProtobufMessage}, {@link ProtobufGroup} or {@link ProtobufMixin},
 * or to an enum constant, in an enum annotated with {@link ProtobufEnum},
 * to indicate the default value of a type.
 * <h2>Usage Example:</h2>
 * <h6>In a {@link ProtobufMessage}:</h6>
 * <pre>{@code
 * @ProtobufMessage
 * public record WrapperMessage(
 *     @ProtobufProperty(index = 1, type = ProtobufType.STRING)
 *     ProtobufString value
 * ) {
 *     private static final WrapperMessage EMPTY = new WrapperMessage(null);
 *
 *     @ProtobufDefaultValue
 *     public static ScalarMessage empty() {
 *         return EMPTY;
 *     }
 * }
 * }</pre>
 * <h6>In a {@link ProtobufMixin}:</h6>
 * <pre>{@code
 * @ProtobufMixin
 * public final class ProtobufAtomicMixin {
 *     @ProtobufDefaultValue
 *     public static AtomicInteger newAtomicInt() {
 *         return new AtomicInteger();
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufDefaultValue {

}
