package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to Java enums to represent a Protobuf enum.
 * All the enum constants in a type annotated with {@link ProtobufEnum} must be annotated with {@link Constant}.
 * If no enum constant is annotated with {@link ProtobufDefaultValue}, {@code null} will be used implicitly.
 * 
 * <h2>Example:</h2>
 * <pre>{@code
 * @ProtobufEnum
 * public enum EnumType {
 *     @ProtobufEnum.Constant(index = 0)
 *     FIRST,
 *     @ProtobufEnum.Constant(index = 1)
 *     SECOND,
 *     @ProtobufEnum.Constant(index = 2)
 *     THIRD
 * }
 * }</pre>
 *
 * @see Constant
 **/
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufEnum {
    /**
     * Specifies the fully qualified name of the referenced Protobuf Enum schema.
     * This is used by the CLI to update schemas.
     *
     * @return the fully qualified name of the Protobuf Enum schema, or empty if it should be detected automatically
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

    /**
     * This annotation can be applied to enum constants in a type annotated with {@link ProtobufEnum}.
     *
     * @see ProtobufEnum
     **/
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Constant {
        /**
         * The minimum valid index for a Protobuf enum constant.
         */
        int MIN_INDEX = -2147483648; // -2^31

        /**
         * Represents the maximum allowable index for a Protobuf enum constant.
         */
        int MAX_INDEX = 2147483647; // 2^31 - 1

        /**
         * Returns the index associated with the Protobuf enum constant.
         *
         * @return the index of the constant, between {@link #MIN_INDEX} and {@link #MAX_INDEX}
         */
        int index();
    }
}
