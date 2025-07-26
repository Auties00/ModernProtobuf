package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to Java enums to represent a Protobuf enum.
 * If no index is annotated with {@link ProtobufEnumIndex}, {@link Enum#ordinal()} will be used implicitly.
 * If no default value is annotated with {@link ProtobufDefaultValue}, {@code null} will be used implicitly.
 * <p>
 * Here is an example of how it can be used:
 * {@snippet :
 * @ProtobufEnum
 * public enum EnumType {
 *     FIRST(0),
 *     SECOND(1),
 *     THIRD(3);
 *
 *     // This could have been annotated with @ProtobufEnumIndex as well
 *     final int index;
 *
 *     EnumType(@ProtobufEnumIndex int index) {
 *         this.index = index;
 *     }
 * }
 * }
 */
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
