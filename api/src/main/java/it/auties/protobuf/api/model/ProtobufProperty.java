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

    Class<? extends ProtobufMessage> implementation() default ProtobufMessage.class;

    boolean required() default false;

    boolean ignore() default false;

    boolean packed() default false;

    boolean repeated() default false;

    @AllArgsConstructor
    @Accessors(fluent = true)
    enum Type {
        MESSAGE(ProtobufMessage.class, ProtobufMessage.class),
        FLOAT(float.class, Float.class),
        DOUBLE(double.class, Double.class),
        BOOLEAN(boolean.class, Boolean.class),
        STRING(String.class, String.class),
        BYTES(byte[].class, byte[].class),
        INT32(int.class, Integer.class),
        SINT32(int.class, Integer.class),
        UINT32(int.class, Integer.class),
        FIXED32(int.class, Integer.class),
        SFIXED32(int.class, Integer.class),
        INT64(long.class, Long.class),
        SINT64(long.class, Long.class),
        UINT64(long.class, Long.class),
        FIXED64(long.class, Long.class),
        SFIXED64(long.class, Long.class);

        @Getter
        @NonNull
        public final Class<?> primitiveType;

        @Getter
        @NonNull
        public final Class<?> wrappedType;

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
