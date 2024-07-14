package it.auties.protobuf.annotation;

import it.auties.protobuf.builtin.ProtobufMapMixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufUnknownFields {
    Class<?>[] mixins() default {
            ProtobufMapMixin.class
    };

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Setter {

    }
}
