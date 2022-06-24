package it.auties.protobuf.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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

    boolean requiresConversion() default false;

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
        @NonNull
        public final Class<?> javaType;

        public boolean isInt() {
            return this == INT32
                    || this == SINT32
                    || this == UINT32
                    || this == FIXED32
                    || this == SFIXED32;
        }

        public boolean isLong() {
            return this == INT64
                    || this == SINT64
                    || this == UINT64
                    || this == FIXED64
                    || this == SFIXED64;
        }
    }
}
