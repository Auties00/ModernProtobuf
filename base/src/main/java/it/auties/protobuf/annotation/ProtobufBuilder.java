package it.auties.protobuf.annotation;

import it.auties.protobuf.builtin.*;
import it.auties.protobuf.model.ProtobufType;

import javax.lang.model.element.Modifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a fluent builder class for protobuf messages with customizable construction logic.
 *
 * <p>This annotation can be applied to static methods or constructors in classes/records
 * annotated with {@link ProtobufMessage} or {@link ProtobufGroup} to auto-generate a builder
 * class that provides a fluent API for constructing instances of the protobuf type.
 *
 * <p>The parameters of the annotated method/constructor must be annotated with {@link PropertyParameter} or {@link SyntheticParameter}.
 *
 * @see PropertyParameter
 * @see SyntheticParameter
 * @see ProtobufMessage
 * @see ProtobufGroup
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufBuilder {
    /**
     * Specifies the visibility and mutability modifiers for the generated builder class.
     *
     * @return an array of modifiers to apply to the generated builder class
     */
    Modifier[] modifiers() default {
            Modifier.PUBLIC,
            Modifier.FINAL
    };

    /**
     * Specifies the suffix or full name of the generated builder class:
     * <ul>
     *   <li><b>Empty name (default):</b> Overrides the existing default builder</li>
     *   <li><b>Non-empty name:</b> Generates an additional builder with the provided name</li>
     * </ul>
     *
     * @return the name of the generated builder class
     */
    String name() default "";

    /**
     * Maps a builder method parameter directly to an existing protobuf property by its field index.
     *
     * <p>Use this annotation when a parameter corresponds exactly to a single protobuf property.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * @ProtobufMessage
     * public record Person(
     *     @ProtobufProperty(index = 1, type = ProtobufType.STRING)
     *     String name,
     *     @ProtobufProperty(index = 2, type = ProtobufType.INT32)
     *     int age
     * ) {
     *     @ProtobufBuilder
     *     static Person of(
     *         @PropertyParameter(index = 1) String name,  // Maps to property index 1
     *         @PropertyParameter(index = 2) int age       // Maps to property index 2
     *     ) {
     *         return new PersonBuilder()
     *             .name(name)
     *             .age(age)
     *             .build();
     *     }
     * }
     * }</pre>
     *
     * @see SyntheticParameter
     */
    @Target({ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @interface PropertyParameter {
        /**
         * The index of the protobuf property this parameter maps to.
         *
         * <p>This must match the {@code index} value of a {@link ProtobufProperty} annotation
         * on a field or record component in the same class.
         *
         * @return the protobuf field index
         */
        long index();
    }

    /**
     * Marks a builder method parameter that doesn't directly map to a single existing protobuf property.
     *
     * <p>Use this annotation for parameters that require custom logic to populate one or more properties,
     * such as union types, computed values, or parameters that transform into multiple fields.
     *
     * <p><b>Common Use Cases:</b></p>
     * <ul>
     *   <li><b>Union types:</b> A single parameter that maps to one of several mutually exclusive properties</li>
     *   <li><b>Computed properties:</b> A parameter that derives multiple property values through calculation</li>
     *   <li><b>Complex types:</b> Custom types that need transformation before being set as properties</li>
     * </ul>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * @ProtobufMessage
     * public record MediaMessage(
     *     @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
     *     ImageMedia image,
     *     @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
     *     VideoMedia video,
     *     @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
     *     AudioMedia audio
     * ) {
     *     sealed interface Media permits ImageMedia, VideoMedia, AudioMedia {}
     *
     *     @ProtobufBuilder
     *     static MediaMessage of(@SyntheticParameter(type = ProtobufType.MESSAGE) Media media) {
     *         var builder = new MediaMessageBuilder();
     *         return switch (media) {
     *             case ImageMedia img -> builder.image(img).build();
     *             case VideoMedia vid -> builder.video(vid).build();
     *             case AudioMedia aud -> builder.audio(aud).build();
     *         };
     *     }
     * }
     * }</pre>
     *
     * @see PropertyParameter
     */
    @Target({ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @interface SyntheticParameter {
        /**
         * The protobuf type of this synthetic parameter.
         * If the type of the parameter is not a protobuf, use {@link ProtobufType#UNKNOWN}.
         *
         * @return the ProtobufType that categorizes this parameter
         */
        ProtobufType type();

        /**
         * Mixin classes that provide serialization/deserialization support for this parameter's type.
         * Only used if {@link #type()} is a valid Protobuf type.
         *
         * @return an array of mixin classes for type conversion
         */
        Class<?>[] mixins() default {
                ProtobufAtomicMixin.class,
                ProtobufOptionalMixin.class,
                ProtobufUUIDMixin.class,
                ProtobufURIMixin.class,
                ProtobufRepeatedMixin.class,
                ProtobufMapMixin.class,
                ProtobufFutureMixin.class
        };
    }
}
