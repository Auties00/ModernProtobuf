package it.auties.protobuf.annotation;

import it.auties.protobuf.builtin.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to fields to designate them as a store for unknown fields
 * encountered while when deserializing the enclosing {@link ProtobufMessage} or {@link ProtobufGroup}.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufUnknownFields {
    /**
     * Returns the array of mixin classes associated with this configuration.
     * These can be used to specify using {@link Setter} how an existing data structure can be used by Protobuf
     * when an unknown field is encountered.
     *
     * @return an array of {@code Class<?>} representing the default mixins
     */
    Class<?>[] mixins() default {
            ProtobufMapMixin.class
    };

    /**
     * This annotation can be applied to non-static methods in a type that is used as an unknown properties store
     * or to static methods in a {@link ProtobufMixin} for an existing data structure.
     * <h2>Usage Example:</h2>
     * <h3>In a custom type:</h3>
     * <pre>{@code
     * final class UnknownFeatures {
     *     private final Set<Integer> unknownFeatures;
     *
     *     UnknownFeatures() {
     *         this.unknownFeatures = new HashSet<>();
     *     }
     *
     *     @ProtobufUnknownFields.Setter
     *     public void addFeature(long index, Object value) {
     *         if (value instanceof Boolean flag && flag) {
     *             unknownFeatures.add(index);
     *         }
     *     }
     *
     *     public boolean hasFeature(long index) {
     *         return unknownFeatures.contains(index);
     *     }
     * }
     * }</pre>
     * <h3>In a {@link ProtobufMixin}:</h3>
     * <pre>{@code
     * @ProtobufMixin
     * final class ProtobufMapMixin {
     *     @ProtobufUnknownFields.Setter
     *     public static void addUnknownField(Map<Long, Object> map, long index, Object value) {
     *         map.put(index, value);
     *     }
     * }
     *}</pre>
     **/
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Setter {

    }
}
