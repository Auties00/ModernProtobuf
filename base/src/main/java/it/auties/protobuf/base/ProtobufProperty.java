package it.auties.protobuf.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufProperty {
    int index();

    ProtobufType type();

    Class<?> implementation() default ProtobufMessage.class;

    boolean required() default false;

    boolean ignore() default false;

    boolean repeated() default false;

    boolean packed() default false;
}
