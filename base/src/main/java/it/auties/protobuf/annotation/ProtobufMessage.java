package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to a type to represent a Protobuf Message.
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @ProtobufMessage
 * record Message(
 *     @ProtobufProperty(index = 1, type = ProtobufType.STRING)
 *     ProtobufString string
 * ) {
 *
 * }
 * }</pre>
 **/
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufMessage {
    /**
     * Specifies the fully qualified name of the referenced Protobuf Message schema.
     * This is used by the CLI to update schemas.
     *
     * @return the fully qualified name of the Protobuf Message schema, or empty if it should be detected automatically
     */
    String name() default "";

    /**
     * Specifies the names that are reserved and cannot be used in the context
     * where this annotation is applied. Reserved names are typically used
     * to ensure compatibility or avoid conflicts in Protobuf definitions.
     *
     * @return an array of strings representing the reserved names
     */
    String[] reservedNames() default {};

    /**
     * Specifies the numeric indexes that are reserved and cannot be used
     * in the context where this annotation is applied. Reserved indexes
     * are typically used to ensure compatibility or avoid conflicts
     * in Protobuf definitions.
     *
     * @return an array of integers representing the reserved indexes
     */
    int[] reservedIndexes() default {};

    /**
     * Specifies the numeric ranges that are reserved and cannot be used
     * in the context where this annotation is applied. Reserved ranges
     * are typically used to ensure compatibility or avoid conflicts
     * in Protobuf definitions.
     *
     * @return an array of {@code ProtobufReservedRange} representing the reserved ranges
     */
    ProtobufReservedRange[] reservedRanges() default {};
}
