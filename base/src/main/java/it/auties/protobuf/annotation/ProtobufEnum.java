package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to Java enums to represent a Protobuf enum.
 * If no index is annotated with {@link ProtobufEnumIndex}, {@link Enum#ordinal()} will be used implicitly.
 * If no default value is annotated with {@link ProtobufDefaultValue}, {@code null} will be used implicitly.
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @ProtobufEnum
 * public enum EnumType {
 *     FIRST,
 *     SECOND,
 *     THIRD
 * }
 * }</pre>
 **/
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufEnum {
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
