package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufMessage {
    String name() default "";
    String[] reservedNames() default {};
    int[] reservedIndexes() default {};
    ProtobufReservedRange[] reservedRanges() default {};
}
