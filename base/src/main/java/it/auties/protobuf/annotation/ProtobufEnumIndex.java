package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to constructor parameters, non-static fields or non-static methods to represent the index of a {@link ProtobufEnum}.
 * If no index is annotated with {@link ProtobufEnumIndex}, {@link Enum#ordinal()} will be used implicitly.
 * <p>
 * Here is an example of how it can be used on a constructor parameter:
 * {@snippet :
 * @ProtobufEnum
 * public enum EnumType {
 *     FIRST(0),
 *     SECOND(1),
 *     THIRD(3);
 *
 *     final int index;
 *
 *     EnumType(@ProtobufEnumIndex int index) {
 *         this.index = index;
 *     }
 * }
 * }
 * <p>
 * Here is an example of how it can be used on a non-static field:
 * {@snippet :
 * @ProtobufEnum
 * public enum EnumType {
 *     FIRST(0),
 *     SECOND(1),
 *     THIRD(3);
 *
 *     @ProtobufEnumIndex
 *     final int index;
 *
 *     EnumType(int index) {
 *         this.index = index;
 *     }
 * }
 * }
 * <p>
 * Here is an example of how it can be used on a non-static method:
 * {@snippet :
 * @ProtobufEnum
 * public enum EnumType {
 *     FIRST(0),
 *     SECOND(1),
 *     THIRD(3);
 *
 *     final int index;
 *
 *     EnumType(int index) {
 *         this.index = index;
 *     }
 *
 *     @ProtobufEnumIndex
 *     int index() {
 *         return index;
 *     }
 * }
 * }
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufEnumIndex {
    /**
     * The minimum valid index for a Protobuf enum constant.
     */
    long MIN_VALUE = 0;

    /**
     * Represents the maximum allowable index for a Protobuf enum constant.
     */
    long MAX_VALUE = 2147483647; // 2^31
}
