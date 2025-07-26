package it.auties.protobuf.annotation;

import it.auties.protobuf.builtin.*;
import it.auties.protobuf.model.ProtobufType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to non-static fields and method to describe a property
 * in a type annotated with {@link ProtobufMessage} or {@link ProtobufGroup}.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufProperty {
    /**
     * The minimum valid index for a Protobuf property.
     */
    long MIN_INDEX = 1;

    /**
     * Represents the maximum allowable index for a Protobuf property.
     */
    long MAX_INDEX = 536_870_911; // 2^29 - 1

    /**
     * Returns the index associated with the Protobuf property.
     *
     * @return the numeric index of the property
     */
    int index();

    /**
     * Returns the type of the Protobuf property.
     *
     * @return the ProtobufType representing the type of the property
     */
    ProtobufType type();

    /**
     * Specifies the key type for a Protobuf map property.
     * Can only be used if {@link ProtobufProperty#type()} is {@link ProtobufType#MAP}
     *
     * @return the {@link ProtobufType} representing the key type of map.
     *         Defaults to {@code ProtobufType.UNKNOWN} if not specified.
     */
    ProtobufType mapKeyType() default ProtobufType.UNKNOWN;

    /**
     * Specifies the value type for a Protobuf map property.
     * Can only be used if {@link ProtobufProperty#type()} is {@link ProtobufType#MAP}
     *
     * @return the {@link ProtobufType} representing the value type of map.
     *         Defaults to {@code ProtobufType.UNKNOWN} if not specified.
     */
    ProtobufType mapValueType() default ProtobufType.UNKNOWN;

    /**
     * Returns the list of mixin classes associated with the current Protobuf property.
     * Mixins provide additional functionalities such as default value generation,
     * serialization, and deserialization support for specific types.
     *
     * @return an array of mixin classes including:
     *         {@link ProtobufAtomicMixin}, {@link ProtobufOptionalMixin}, {@link ProtobufUUIDMixin},
     *         {@link ProtobufURIMixin}, {@link ProtobufRepeatedMixin}, {@link ProtobufMapMixin},
     *         {@link ProtobufFutureMixin}, and {@link ProtobufLazyMixin}
     */
    Class<?>[] mixins() default {
            ProtobufAtomicMixin.class,
            ProtobufOptionalMixin.class,
            ProtobufUUIDMixin.class,
            ProtobufURIMixin.class,
            ProtobufRepeatedMixin.class,
            ProtobufMapMixin.class,
            ProtobufFutureMixin.class,
            ProtobufLazyMixin.class
    };

    /**
     * Indicates whether a Protobuf property is required.
     *
     * @return true if the property is required; false otherwise
     */
    boolean required() default false;

    /**
     * Indicates whether the annotated Protobuf property should be ignored during serialization and deserialization.
     *
     * @return true if the property is ignored; false otherwise
     */
    boolean ignored() default false;

    /**
     * Indicates whether the associated Protobuf property is packed when serialized.
     * Packed encoding is applicable for repeated fields of primitive data types in Protobuf
     * and results in more compact encoding by omitting field tags for each element.
     *
     * @return {@code true} if the field should use packed encoding; {@code false} otherwise.
     */
    boolean packed() default false;
}
