package it.auties.protobuf.annotation;

import it.auties.protobuf.stream.ProtobufOutputStream;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to non-static methods,
 * in a type annotated with {@link ProtobufMixin}
 * or in a type that should be interpreted as a {@link ProtobufMessage}, {@link ProtobufGroup} or {@link ProtobufEnum}.
 * <h2>Usage Example:</h2>
 * <h3>In a {@link ProtobufMessage}:</h3>
 * <pre>{@code
 * @ProtobufMessage
 * record BirthdayDate(int day, int month, int year) {
 *     @ProtobufDeserializer
 *     static BirthdayDate of(String date) {
 *         if (date == null) {
 *             return null;
 *         }
 *
 *         var dateParts = date.split("/", 3);
 *         if (dateParts.length != 3) {
 *             return null;
 *         }
 *
 *         try {
 *             var day = Integer.parseUnsignedInt(dateParts[0]);
 *             var month = Integer.parseUnsignedInt(dateParts[1]);
 *             var year = Integer.parseUnsignedInt(dateParts[2]);
 *             return new BirthdayDate(day, month, year);
 *         } catch (NumberFormatException exception) {
 *             return null;
 *         }
 *     }
 *
 *     @ProtobufSerializer
 *     public String formatted() {
 *         return "%s/%s/%s".formatted(day, month, year);
 *     }
 * }
 * }</pre>
 * <h3>In a {@link ProtobufGroup}:</h3>
 * <pre>{@code
 * @ProtobufGroup
 * record Coordinates(double latitude, double longitude) {
 *     @ProtobufDeserializer
 *     static Coordinates parse(String coordinates) {
 *         if (coordinates == null || coordinates.isBlank()) {
 *             return null;
 *         }
 *
 *         var parts = coordinates.split(",", 2);
 *         if (parts.length != 2) {
 *             throw new IllegalArgumentException("Invalid coordinates format");
 *         }
 *
 *         try {
 *             var latitude = Double.parseDouble(parts[0].trim());
 *             var longitude = Double.parseDouble(parts[1].trim());
 *             return new Coordinates(latitude, longitude);
 *         } catch (NumberFormatException exception) {
 *             throw new IllegalArgumentException("Invalid coordinate values", exception);
 *         }
 *     }
 *
 *     @ProtobufSerializer
 *     public String format() {
 *         return "%f,%f".formatted(latitude, longitude);
 *     }
 * }
 * }</pre>
 * <h3>In a {@link ProtobufEnum}:</h3>
 * <pre>{@code
 * @ProtobufEnum
 * public enum Status {
 *     @ProtobufEnumConstant(index = 0)
 *     ACTIVE,
 *
 *     @ProtobufEnumConstant(index = 1)
 *     INACTIVE,
 *
 *     @ProtobufEnumConstant(index = 2)
 *     SUSPENDED;
 *
 *     @ProtobufDeserializer
 *     static Status fromString(String status) {
 *         return status == null ? ACTIVE : valueOf(status.toUpperCase());
 *     }
 *
 *     @ProtobufSerializer
 *     public String toStringValue() {
 *         return name().toLowerCase();
 *     }
 * }
 * }</pre>
 * <h3>In a {@link ProtobufMixin}:</h3>
 * <pre>{@code
 * @ProtobufMixin
 * public final class ProtobufAtomicMixin {
 *     @ProtobufDeserializer
 *     public static AtomicInteger ofAtomic(Integer value) {
 *         return value == null ? new AtomicInteger() : new AtomicInteger(value);
 *     }
 *
 *     @ProtobufSerializer
 *     public static int toInt(AtomicInteger value) {
 *         return value.get();
 *     }
 * }
 * }</pre>
 * <h2>Low-Level Serialization:</h2>
 * <p>For advanced use cases, you can serialize directly to a {@code ProtobufOutputStream}.
 * <h3>To a Protobuf stream:</h3>
 * <pre>{@code
 * @ProtobufMessage
 * record BirthdayDate(int day, int month, int year) {
 *     @ProtobufDeserializer
 *     static BirthdayDate of(ProtobufInputStream stream) {
 *         var length = stream.readLength();
 *
 *         int digit;
 *
 *         var day = 0;
 *         while (length-- > 0) {
 *             var value = stream.readByte();
 *             digit = value - '0';
 *             if (digit == '/') {
 *                 break;
 *             } else if (digit >= 0 && digit <= 9) {
 *                 day = day * 10 + digit;
 *             } else {
 *                 throw new IllegalArgumentException("Invalid digit: " + digit);
 *             }
 *         }
 *
 *         var month = 0;
 *         while (length-- > 0) {
 *             var value = stream.readByte();
 *             digit = value - '0';
 *             if (digit == '/') {
 *                 break;
 *             } else if (digit >= 0 && digit <= 9) {
 *                 month = month * 10 + digit;
 *             } else {
 *                 throw new IllegalArgumentException("Invalid digit: " + digit);
 *             }
 *         }
 *
 *         var year = 0;
 *         while (length-- > 0) {
 *             var value = stream.readByte();
 *             digit = value - '0';
 *             if (digit >= 0 && digit <= 9) {
 *                 year = year * 10 + digit;
 *             } else {
 *                 throw new IllegalArgumentException("Invalid digit: " + digit);
 *             }
 *         }
 *
 *         return new BirthdayDate(day, month, year);
 *     }
 *
 *     @ProtobufSerializer
 *     void writeToStream(ProtobufOutputStream stream) {
 *         stream.writeString(String.valueOf(day));
 *         stream.writeString("/");
 *         stream.writeString(String.valueOf(month));
 *         stream.writeString("/");
 *         stream.writeString(String.valueOf(year));
 *     }
 *
 *     @ProtobufSize
 *     int size() {
 *         int daySize;
 *         if(v < 0) {
 *             throw new InternalError("Invalid day: negative");
 *         } else if(v > 31) {
 *             throw new InternalError("Invalid day: over 31");
 *         }else if(v < 10) {
 *             daySize = 1;
 *         }else {
 *             daySize = 2;
 *         }
 *
 *         int monthSize;
 *         if(v < 0) {
 *             throw new InternalError("Invalid month: negative");
 *         } else if(v > 12) {
 *             throw new InternalError("Invalid month: over 12");
 *         }else if(v < 10) {
 *             monthSize = 1;
 *         }else {
 *             monthSize = 2;
 *         }
 *
 *         var yearSize = String.valueOf(year).length();
 *
 *         var totalLength = daySize + 1 + monthSize + 1 + yearSize;
 *
 *         return ProtobufOutputStream.getVarIntSize(totalLength) + totalLength;
 *     }
 *   }
 * }</pre>
 *
 * @apiNote Implementing {@link ProtobufSize} is optional if the size of the serializer's return type can be calculated automatically,
 *          but mandatory when writing directly to a {@link  ProtobufOutputStream}, as the size cannot be inferred.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufSerializer {
    /**
     * Provides an optional warning message that should be printed by the compiler when this serializer is used.
     * By default, no warning message is specified.
     *
     * @return a string containing the warning message; if not specified, an empty string is returned
     */
    String warning() default "";
}
