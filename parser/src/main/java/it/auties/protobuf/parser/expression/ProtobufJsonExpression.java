package it.auties.protobuf.parser.expression;

import it.auties.protobuf.parser.tree.ProtobufMessageStatement;

import java.util.Collections;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * Represents a message or group as a JSON expression in the Protocol Buffer AST.
 * <p>
 * Message/Group literal expressions represent structured data in the Protocol Buffer text format,
 * allowing inline specification of group values. They are used in custom option values that expect groups.
 * <h2>Example:</h2>
 * <pre>{@code
 * option (my_option) = {
 *   field1: "value"
 *   field2: 42
 *   nested_message: {
 *     inner_field: true
 *   }
 * };
 * }</pre>
 *
 * @see ProtobufExpression
 * @see ProtobufMessageStatement
 */
public record ProtobufJsonExpression(SequencedMap<String, ProtobufExpression> value) implements ProtobufExpression {
    public ProtobufJsonExpression {
        Objects.requireNonNull(value, "value cannot be null");
    }

    /**
     * Returns an unmodifiable view of the field name to expression mappings.
     * <p>
     * The map preserves the order in which fields were added.
     * </p>
     *
     * @return unmodifiable sequenced map of field data
     */
    public SequencedMap<String, ProtobufExpression> value() {
        return Collections.unmodifiableSequencedMap(value);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append("{");
        for(var entry : value.entrySet()){
            builder.append("    \"");
            builder.append(entry.getKey());
            builder.append("\"");
            builder.append(": ");
            builder.append(entry.getValue());
        }
        builder.append("}");
        return builder.toString();
    }
}
