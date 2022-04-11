package it.auties.protobuf.model;

import it.auties.protobuf.exception.ProtobufException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Optional;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public @interface ProtobufProperty {
    int index();
    Type type();
    Class<?> concreteType() default Object.class;
    boolean required() default false;
    boolean ignore() default false;
    boolean packed() default false;
    boolean repeated() default false;

    @AllArgsConstructor
    @Accessors(fluent = true)
    enum Type {
        MESSAGE(ProtobufMessage.class),
        FLOAT(float.class),
        DOUBLE(double.class),
        BOOLEAN(boolean.class),
        STRING(String.class),
        BYTES(byte[].class),
        INT32(int.class),
        SINT32(int.class),
        UINT32(int.class),
        FIXED32(int.class),
        SFIXED32(int.class),
        INT64(long.class),
        SINT64(long.class),
        UINT64(long.class),
        FIXED64(long.class),
        SFIXED64(long.class);

        @Getter
        public final Class<?> javaType;

        public static Type forJavaType(Class<?> clazz){
            return Arrays.stream(values())
                    .filter(entry -> entry.javaType().isAssignableFrom(clazz))
                    .findFirst()
                    .orElseThrow(() -> new ProtobufException("%s is not a valid type: only Java built in types and messages can be used inside a protobuf message".formatted(clazz.getName())));
        }
    }
}
