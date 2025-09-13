package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to a type to represent a Protobuf Mixin.
 * Protobuf mixins are used to provide additional on existing types that cannot be modified, like built-in Java types.
 * This library provides a number of built-in mixins for common use cased under the {@link it.auties.protobuf.builtin} package.
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @ProtobufMixin
 * public final class ProtobufURIMixin {
 *     @ProtobufDeserializer
 *     public static URI ofNullable(ProtobufString value) {
 *         return value == null ? null : URI.create(value.toString());
 *     }
 *
 *     @ProtobufSerializer
 *     public static ProtobufString toValue(URI value) {
 *         return value == null ? null : ProtobufString.wrap(value.toString());
 *     }
 * }
 * }</pre>
 *
 * @see ProtobufSerializer
 * @see ProtobufDeserializer
 * @see ProtobufDefaultValue
 **/
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufMixin {

}
