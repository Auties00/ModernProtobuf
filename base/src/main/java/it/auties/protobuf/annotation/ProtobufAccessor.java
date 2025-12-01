package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to non-static methods,
 * in a type annotated with {@link ProtobufMessage} or {@link ProtobufGroup},
 * to represent a custom accessor for an existing {@link ProtobufProperty} with the same index.
 * <h2>Usage Example:</h2>
 * <h3>In a {@link ProtobufMessage}:</h3>
 * <pre>{@code
 * @ProtobufMessage
 * public final class BoxedMessage {
 *     @ProtobufProperty(index = 1, type = ProtobufType.STRING)
 *     private final String value;
 *
 *     public BoxedMessage(String value) {
 *         this.value = value;
 *     }
 *
 *     @ProtobufAccessor(index = 1)
 *     public String getValue() {
 *         return value;
 *     }
 * }
 * }</pre>
 * <h3>In a {@link ProtobufGroup}:</h3>
 * <pre>{@code
 * @ProtobufGroup
 * public final class MessageGroup {
 *     @ProtobufProperty(index = 1, type = ProtobufType.INT32)
 *     private final int id;
 *
 *     @ProtobufProperty(index = 2, type = ProtobufType.STRING)
 *     private final String name;
 *
 *     public MessageGroup(int id, String name) {
 *         this.id = id;
 *         this.name = name;
 *     }
 *
 *     @ProtobufAccessor(index = 1)
 *     public int getId() {
 *         return id;
 *     }
 *
 *     @ProtobufAccessor(index = 2)
 *     public String getName() {
 *         return name;
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufAccessor {
    /**
     * Returns the index of the associated {@link ProtobufProperty}
     *
     * @return the numeric index of the associated property
     */
    long index();
}
