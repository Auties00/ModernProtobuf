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
    String warning() default "";
}
