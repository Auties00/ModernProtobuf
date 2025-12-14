package it.auties.protobuf.parser.expression;

import it.auties.protobuf.parser.tree.ProtobufEnumStatement;

import java.util.Objects;

/**
 * Represents an enum constant reference expression in the Protocol Buffer AST.
 * <p>
 * Enum constant expressions represent references to enum values by name, typically used in:
 * </p>
 * <ul>
 *   <li>Default values for enum fields ({@code default = STATUS_UNKNOWN})</li>
 *   <li>Option values that expect enum constants</li>
 *   <li>Message literal values for enum fields</li>
 * </ul>
 * <p>
 *
 * @see ProtobufExpression
 * @see ProtobufEnumStatement
 */
public record ProtobufEnumConstantExpression(String name) implements ProtobufExpression {
    public ProtobufEnumConstantExpression {
        Objects.requireNonNull(name, "name is null");
    }

    @Override
    public String toString() {
        return name;
    }
}
