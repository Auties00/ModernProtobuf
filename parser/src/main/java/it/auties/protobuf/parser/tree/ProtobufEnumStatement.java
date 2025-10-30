package it.auties.protobuf.parser.tree;

import java.util.Objects;

/**
 * Represents an enum type declaration in the Protocol Buffer AST.
 * <p>
 * Enums define a type with a restricted set of named integer constants. Each enum value consists
 * of a name and an associated numeric value. Enums can be defined at file level or nested within
 * messages.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * // Simple enum
 * enum Status {
 *   UNKNOWN = 0;
 *   PENDING = 1;
 *   APPROVED = 2;
 *   REJECTED = 3;
 * }
 *
 * // Enum with aliases (allow_alias option)
 * enum Priority {
 *   option allow_alias = true;
 *   PRIORITY_UNSPECIFIED = 0;
 *   LOW = 1;
 *   MEDIUM = 2;
 *   HIGH = 3;
 *   CRITICAL = 3;  // Alias for HIGH
 * }
 *
 * // Enum with reserved values
 * enum Color {
 *   reserved 2, 15, 9 to 11;
 *   reserved "FOO", "BAR";
 *   COLOR_UNKNOWN = 0;
 *   RED = 1;
 * }
 * }</pre>
 * <p>
 * Enums can contain various child elements:
 * </p>
 * <ul>
 *   <li><strong>Enum constants:</strong> Named values with associated integer numbers</li>
 *   <li><strong>Options:</strong> Configuration options for the enum (e.g., allow_alias)</li>
 *   <li><strong>Reserved:</strong> Reserved field numbers or names to prevent reuse</li>
 *   <li><strong>Empty statements:</strong> Standalone semicolons</li>
 * </ul>
 * <p>
 * Important enum requirements:
 * </p>
 * <ul>
 *   <li>In proto3, the first enum value must be zero</li>
 *   <li>By default, enum values must be unique; use {@code allow_alias = true} to allow aliases</li>
 *   <li>Enum value numbers must be within the 32-bit integer range</li>
 * </ul>
 * <p>
 * This class implements multiple child marker interfaces, allowing enums to appear at file level,
 * nested within messages, inside extend blocks, or as group field types.
 * </p>
 * <p>
 * Enums are attributed when they have a name and all their child statements are attributed.
 * During semantic analysis, enum values are validated for uniqueness and proto3 zero-value requirements.
 * </p>
 *
 * @see ProtobufEnumChild
 * @see ProtobufEnumConstantStatement
 * @see ProtobufReservedStatement
 */
public final class ProtobufEnumStatement
        extends ProtobufStatementWithBodyImpl<ProtobufEnumChild>
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithBody<ProtobufEnumChild>, ProtobufTree.WithBodyAndName<ProtobufEnumChild>,
                   ProtobufDocumentChild, ProtobufMessageChild, ProtobufGroupChild, ProtobufExtendChild {
    private String name;

    /**
     * Constructs a new enum statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufEnumStatement(int line) {
        super(line);
    }

    /**
     * Returns the enum name.
     *
     * @return the enum name, or null if not yet set
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Checks whether this enum has a name assigned.
     *
     * @return true if a name is present, false otherwise
     */
    @Override
    public boolean hasName() {
        return name != null;
    }

    /**
     * Sets the name for this enum.
     * <p>
     * The name must be a valid Protocol Buffer identifier and unique within the parent scope.
     * </p>
     *
     * @param name the enum name to set
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("enum");
        builder.append(" ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");

        builder.append("{");
        builder.append("\n");

        if(children.isEmpty()) {
            builder.append("\n");
        } else {
            children.forEach(statement -> {
                builder.append("    ");
                builder.append(statement);
                builder.append("\n");
            });
        }

        builder.append("}");

        return builder.toString();
    }
}
