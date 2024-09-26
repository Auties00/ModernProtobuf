package it.auties.protobuf.annotation;

import it.auties.protobuf.builtin.*;
import it.auties.protobuf.model.ProtobufType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufSerializer {
    GroupProperty[] groupProperties() default {};

    @interface GroupProperty {
        int index();
        ProtobufType type();
        ProtobufType mapKeyType() default ProtobufType.UNKNOWN;
        ProtobufType mapValueType() default ProtobufType.UNKNOWN;
        Class<?>[] mixins() default {
                ProtobufAtomicMixin.class,
                ProtobufOptionalMixin.class,
                ProtobufUUIDMixin.class,
                ProtobufURIMixin.class,
                ProtobufRepeatedMixin.class,
                ProtobufMapMixin.class,
                ProtobufFutureMixin.class,
                ProtobufLazyMixin.class
        };
        boolean packed() default false;
        Class<?> implementation() default Object.class;
        Class<?> mapValueImplementation() default Object.class;
        Class<?> repeatedValueImplementation() default Object.class;
    }
}
