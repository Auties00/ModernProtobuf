package it.auties.protobuf.annotation;

import it.auties.protobuf.builtin.*;
import it.auties.protobuf.model.ProtobufType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufProperty {
    int index();

    ProtobufType type();

    Class<?> overrideType() default Object.class;

    Class<?> overrideRepeatedType() default Object.class;

    Class<?> overrideMapType() default Object.class;

    ProtobufType mapKeyType() default ProtobufType.MAP;

    Class<?> overrideMapKeyType() default Object.class;

    ProtobufType mapValueType() default ProtobufType.MAP;

    Class<?> overrideMapValueType() default Object.class;

    Class<?>[] mixins() default {
            ProtobufAtomicMixin.class,
            ProtobufOptionalMixin.class,
            ProtobufUUIDMixin.class,
            ProtobufURIMixin.class,
            ProtobufRepeatedMixin.class,
            ProtobufMapMixin.class,
            ProtobufFutureMixin.class
    };

    boolean required() default false;

    boolean ignored() default false;

    boolean packed() default false;
}
