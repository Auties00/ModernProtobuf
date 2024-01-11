package it.auties.protobuf.annotation;

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

    ProtobufType keyType() default ProtobufType.MAP;

    ProtobufType valueType() default ProtobufType.MAP;

    Class<?> overrideType() default Object.class;

    Class<? extends ProtobufMixin> mixin() default ProtobufMixin.class;

    boolean required() default false;

    boolean ignored() default false;

    boolean packed() default false;
}
