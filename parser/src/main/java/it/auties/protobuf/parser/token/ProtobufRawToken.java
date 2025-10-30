package it.auties.protobuf.parser.token;

import java.util.Objects;

/**
 * Represents a raw token produced by the Protocol Buffer lexer.
 * <p>
 * Raw tokens represent identifiers, keywords, operators, and other non-literal elements
 * of the Protocol Buffer language. Examples include:
 * </p>
 * <ul>
 *   <li>Keywords: {@code message}, {@code enum}, {@code service}, {@code syntax}, etc.</li>
 *   <li>Identifiers: user-defined names for messages, fields, enums, etc.</li>
 *   <li>Operators and delimiters: {@code {}, {@code }}, {@code ;}, {@code =}, {@code [}, {@code ]}, etc.</li>
 *   <li>Type names: {@code int32}, {@code string}, {@code bool}, etc.</li>
 * </ul>
 *
 * @param value the string value of the raw token, must not be null
 */
public record ProtobufRawToken(String value) implements ProtobufToken {
    public ProtobufRawToken {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public String toString() {
        return value;
    }
}
