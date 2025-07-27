package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to non-static methods to represent the getter for an existing {@link ProtobufProperty} with the same index.
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @ProtobufMessage
 * public final class BoxedMessage {
 *     @ProtobufProperty(index = 1, type = ProtobufType.STRING)
 *     private final ProtobufString value;
 *
 *     public BoxedMessage(String value) {
 *         this.value = value;
 *     }
 *
 *     @ProtobufGetter(index = 1)
 *     public ProtobufString unbox() {
 *         return value;
 *     }
 * }
 * }</pre>
 **/
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufGetter {
    /**
     * Returns the index of the associated {@link ProtobufProperty}
     *
     * @return the numeric index of the associated property
     */
    int index();
}
