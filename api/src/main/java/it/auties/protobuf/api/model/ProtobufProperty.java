package it.auties.protobuf.api.model;

import it.auties.protobuf.api.exception.ProtobufException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Optional;

import static it.auties.protobuf.api.util.WireType.*;
import static it.auties.protobuf.api.util.WireType.WIRE_TYPE_FIXED64;

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
        MESSAGE(ProtobufMessage.class, Enum.class),
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

        public final Class<?> aliasType;

        Type(Class<?> javaType){
            this(javaType, null);
        }

        public Optional<Class<?>> aliasType() {
            return Optional.ofNullable(aliasType);
        }

        public int tag(){
            return switch (this){
                case BOOLEAN, INT32, SINT32, UINT32, INT64, SINT64, UINT64 -> WIRE_TYPE_VAR_INT;
                case STRING, BYTES, MESSAGE -> WIRE_TYPE_LENGTH_DELIMITED;
                case FLOAT, FIXED32, SFIXED32 -> WIRE_TYPE_FIXED32;
                case DOUBLE, FIXED64, SFIXED64 -> WIRE_TYPE_FIXED64;
            };
        }

        public boolean isInt(){
            return this == INT32
                    || this == SINT32
                    || this == UINT32
                    || this == FIXED32
                    || this == SFIXED32;
        }

        public boolean isLong(){
            return this == INT64
                    || this == SINT64
                    || this == UINT64
                    || this == FIXED64
                    || this == SFIXED64;
        }

        public static Type forJavaType(Class<?> clazz){
            return Arrays.stream(values())
                    .filter(entry -> entry.javaType().isAssignableFrom(clazz)
                            || entry.aliasType().filter(type -> type.isAssignableFrom(clazz)).isPresent())
                    .findFirst()
                    .orElseThrow(() -> new ProtobufException("%s is not a valid type: only Java built in types and messages can be used inside a protobuf message".formatted(clazz.getName())));
        }
    }
}
