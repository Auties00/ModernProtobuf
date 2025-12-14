package it.auties.protobuf.parser.token;

import it.auties.protobuf.parser.number.ProtobufNumber;

import java.util.Objects;

/**
 * Represents a numeric literal token produced by the Protocol Buffer lexer.
 * <p>
 * Number tokens represent numeric values in various formats supported by the Protocol Buffer language:
 * </p>
 * <ul>
 *   <li>Decimal integers: {@code 42}, {@code -123}</li>
 *   <li>Hexadecimal integers: {@code 0x2A}, {@code 0xFF}</li>
 *   <li>Octal integers: {@code 052}, {@code 0177}</li>
 *   <li>Floating-point numbers: {@code 3.14}, {@code -0.5}, {@code 1e10}</li>
 *   <li>Special floating-point values: {@code inf}, {@code -inf}, {@code nan}</li>
 * </ul>
 * <p>
 * The {@link ProtobufNumber} value encapsulates the parsed numeric value, which can be either
 * an integer or a floating-point number with arbitrary precision.
 * </p>
 *
 * @param value the numeric value represented by this token, must not be null
 */
public record ProtobufNumberToken(ProtobufNumber value) implements ProtobufToken {
    public ProtobufNumberToken {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
