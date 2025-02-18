package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufDeserializer {
    String warning() default "";
    BuilderBehaviour builderBehaviour() default BuilderBehaviour.DISCARD;

    enum BuilderBehaviour {
        DISCARD,
        ADD,
        OVERRIDE
    }
}
