package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to a type to represent a Protobuf Mixin.
 * Protobuf mixins are used to provide additional on existing types that cannot be modified, like built-in Java types.
 * This library provides a number of built-in mixins for common use cased under the {@link it.auties.protobuf.builtin} package.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufMixin {

}
