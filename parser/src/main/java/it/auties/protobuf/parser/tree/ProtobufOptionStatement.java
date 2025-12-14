package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.expression.ProtobufExpression;
import it.auties.protobuf.parser.expression.ProtobufOptionExpression;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents an option statement in the Protocol Buffer AST.
 * <p>
 * Option statements configure behavior of Protocol Buffer elements at various scopes.
 * They appear as standalone statements (not in square brackets like inline field options).
 * Options can be standard Protocol Buffer options or custom extensions.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * // File-level options
 * option java_package = "com.example.proto";
 * option java_outer_classname = "MyProtos";
 * option optimize_for = SPEED;
 *
 * // Custom extension option
 * option (my.custom.option) = {
 *   field: "value"
 * };
 * }</pre>
 * <p>
 * This class implements multiple child marker interfaces, allowing options to appear in:
 * documents, messages, enums, oneofs, services, methods, and extend blocks.
 * </p>
 * <p>
 * During semantic analysis, the option name is resolved to its definition (a field in descriptor.proto)
 * and the value's type is validated.
 * </p>
 *
 * @see ProtobufOptionName
 * @see ProtobufOptionExpression
 */
public final class ProtobufOptionStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufDocumentChild, ProtobufMessageChild, ProtobufEnumChild, ProtobufOneofChild, ProtobufServiceChild, ProtobufMethodChild {
    private ProtobufOptionName name;
    private ProtobufExpression value;
    private ProtobufOptionDefinition definition;

    /**
     * Constructs a new option statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufOptionStatement(int line) {
        super(line);
    }

    @Override
    public boolean isAttributed() {
        return hasName()
               && hasDefinition()
               && hasValue();
    }

    /**
     * Returns the option name.
     *
     * @return the option name, or null if not yet set
     */
    public ProtobufOptionName name() {
        return name;
    }

    /**
     * Checks whether this option has a name assigned.
     *
     * @return true if a name is present, false otherwise
     */
    public boolean hasName() {
        return name != null;
    }

    /**
     * Sets the option name.
     *
     * @param name the option name to set
     */
    public void setName(ProtobufOptionName name) {
        this.name = name;
    }

    /**
     * Returns the field definition for this option from descriptor.proto.
     * <p>
     * This is populated during semantic analysis when the option name is resolved.
     * </p>
     *
     * @return optional containing the field definition, or empty if not yet attributed
     */
    public Optional<ProtobufOptionDefinition> definition() {
        return Optional.ofNullable(definition);
    }

    /**
     * Checks whether this option has been attributed with a definition.
     *
     * @return true if a definition is present, false otherwise
     */
    public boolean hasDefinition() {
        return definition != null;
    }

    /**
     * Sets the field definition for this option.
     *
     * @param definition the field definition from descriptor.proto
     */
    public void setDefinition(ProtobufOptionDefinition definition) {
        this.definition = definition;
    }

    /**
     * Returns the value expression for this option.
     *
     * @return the value expression, or null if not yet set
     */
    public ProtobufExpression value() {
        return value;
    }

    /**
     * Checks whether this option has a value assigned.
     *
     * @return true if a value is present, false otherwise
     */
    public boolean hasValue() {
        return value != null;
    }

    /**
     * Sets the value expression for this option.
     *
     * @param value the value expression to set
     */
    public void setValue(ProtobufExpression value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtobufOptionStatement that
               && Objects.equals(this.name(), that.name())
               && Objects.equals(this.value(), that.value());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("option");
        builder.append(" ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);

        builder.append(" ");
        builder.append("=");
        builder.append(" ");

        var value = Objects.requireNonNullElse(this.value, "[missing]");
        builder.append(value);

        builder.append(";");

        return builder.toString();
    }
}
