package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

/**
 * Represents a message literal value expression in the Protocol Buffer AST.
 * <p>
 * Message literal expressions represent structured data in the Protocol Buffer text format,
 * allowing inline specification of message values. They are used in:
 * </p>
 * <ul>
 *   <li>Default values for message fields</li>
 *   <li>Custom option values that expect messages</li>
 *   <li>Text format message literals</li>
 * </ul>
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
 * <p>
 * The message literal contains a map of field names to their expression values, maintaining
 * insertion order. During semantic analysis, the type is resolved to the message definition.
 * </p>
 *
 * @see ProtobufExpression
 * @see ProtobufMessageStatement
 */
public final class ProtobufMessageValueExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    private final SequencedMap<String, ProtobufExpression> data;
    private ProtobufTypeReference type;

    /**
     * Constructs a new message value expression at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufMessageValueExpression(int line) {
        super(line);
        this.data = new LinkedHashMap<>();
    }

    /**
     * Returns an unmodifiable view of the field name to expression mappings.
     * <p>
     * The map preserves the order in which fields were added.
     * </p>
     *
     * @return unmodifiable sequenced map of field data
     */
    public SequencedMap<String, ProtobufExpression> data() {
        return Collections.unmodifiableSequencedMap(data);
    }

    /**
     * Adds a field and its value to this message literal.
     *
     * @param key the field name
     * @param value the field value expression
     */
    public void addData(String key, ProtobufExpression value) {
        data.put(key, value);
    }

    /**
     * Removes a field from this message literal.
     *
     * @param key the field name to remove
     * @return the removed expression, or null if the field was not present
     */
    public ProtobufExpression removeData(String key) {
        return data.remove(key);
    }

    /**
     * Returns the type reference to the message this literal represents.
     * <p>
     * This is populated during semantic analysis after type resolution.
     * </p>
     *
     * @return the message type reference, or null if not yet attributed
     */
    public ProtobufTypeReference type() {
        return type;
    }

    /**
     * Checks whether this expression has been attributed with a type.
     *
     * @return true if a type is present, false otherwise
     */
    public boolean hasType() {
        return type != null;
    }

    /**
     * Sets the type reference for this message literal.
     *
     * @param type the message type reference
     */
    public void setType(ProtobufTypeReference type) {
        this.type = type;
    }

    @Override
    public boolean isAttributed() {
        return type != null;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append("{");
        for(var entry : data.entrySet()){
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
