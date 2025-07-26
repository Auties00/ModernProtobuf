package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to non-static methods to represent the getter for an existing {@link ProtobufProperty} with the same index.
 * <p>
 * Here is an example of how it can be used:
 * {@snippet :
 * @ProtobufMessage
 * public final class BoxMessage {
 *     @ProtobufProperty(index = 1, type = ProtobufType.STRING)
 *     private final ProtobufString value;
 *
 *     public BoxMessage(String value) {
 *         this.value = value;
 *     }
 *
 *     @ProtobufGetter(index = 1)
 *     public ProtobufString unbox() {
 *         return value;
 *     }
 * }
 * }
 */
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
