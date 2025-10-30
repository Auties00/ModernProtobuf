package it.auties.protobuf.parser.tree;

import java.util.Objects;

/**
 * Represents an enum constant (value) declaration in the Protocol Buffer AST.
 * <p>
 * Enum constants are the named integer values that comprise an enum type. Each constant
 * has a name and an associated numeric value. Multiple constants can have the same numeric
 * value when the {@code allow_alias} option is enabled.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * enum Status {
 *   UNKNOWN = 0;
 *   PENDING = 1;
 *   APPROVED = 2;
 *   REJECTED = 3;
 * }
 *
 * // With options
 * enum Priority {
 *   LOW = 1 [deprecated = true];
 *   MEDIUM = 2;
 *   HIGH = 3;
 * }
 *
 * // With aliases
 * enum Result {
 *   option allow_alias = true;
 *   SUCCESS = 0;
 *   OK = 0;  // Alias for SUCCESS
 * }
 * }</pre>
 * <p>
 * Important requirements for enum constants:
 * </p>
 * <ul>
 *   <li>In proto3, the first enum value must be zero for default value semantics</li>
 *   <li>Constant names must be unique within the enum</li>
 *   <li>Numeric values must be unique unless {@code allow_alias = true}</li>
 *   <li>Values must fit within the 32-bit integer range</li>
 * </ul>
 * <p>
 * This class extends {@link ProtobufFieldStatement} but reuses only the name, index (numeric value),
 * and options fields. The type and modifier fields are not used for enum constants.
 * </p>
 *
 * @see ProtobufEnumChild
 * @see ProtobufEnumStatement
 * @see ProtobufFieldStatement
 */
public final class ProtobufEnumConstantStatement
        extends ProtobufFieldStatement
        implements ProtobufEnumChild {
    /**
     * Constructs a new enum constant statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufEnumConstantStatement(int line) {
        super(line);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" = ");
        var index = Objects.requireNonNull(this.index, "[missing]");
        builder.append(index);
        writeOptions(builder);
        builder.append(";");
        return builder.toString();
    }
}
