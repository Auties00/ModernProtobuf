package it.auties.protobuf.parser.expression;

import it.auties.protobuf.parser.tree.ProtobufOptionName;

import java.util.Objects;

/**
 * Represents an option key-value pair expression in the Protocol Buffer AST.
 * <p>
 * Options are metadata annotations that configure behavior of Protocol Buffer elements.
 * They appear in square brackets and consist of a name and a value:
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * // Field options
 * optional string name = 1 [deprecated = true, default = "unknown"];
 *
 * // File-level option statement
 * option java_package = "com.example.proto";
 *
 * // Custom extension option
 * option (my.custom.option) = {
 *   field: "value"
 * };
 * }</pre>
 * <p>
 *
 * @see ProtobufOptionName
 * @see ProtobufExpression
 */
public record ProtobufOptionExpression(ProtobufOptionName name, ProtobufExpression value) implements ProtobufExpression {
    public ProtobufOptionExpression {
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtobufOptionExpression(var thatName, var thatValue)
               && Objects.equals(this.name, thatName)
               && Objects.equals(this.value, thatValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);

        builder.append(" ");
        builder.append("=");
        builder.append(" ");

        var value = Objects.requireNonNullElse(this.value, "[missing]");
        builder.append(value);

        return builder.toString();
    }
}
