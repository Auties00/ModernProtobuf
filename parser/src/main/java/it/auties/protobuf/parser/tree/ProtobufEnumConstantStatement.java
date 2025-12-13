package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufInteger;

import java.util.*;
import java.util.stream.Collectors;

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
        extends ProtobufStatementImpl
        implements ProtobufTree.WithName, ProtobufTree.WithIndex, ProtobufTree.WithOptions,
                   ProtobufEnumChild {
    private String name;
    private ProtobufInteger index;
    private final SequencedMap<String, ProtobufOptionExpression> options;

    /**
     * Constructs a new enum constant statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufEnumConstantStatement(int line) {
        super(line);
        this.options = new LinkedHashMap<>();
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" = ");
        var index = Objects.requireNonNull(this.index, "[missing]");
        builder.append(index);
        var options = this.options.sequencedEntrySet();
        if (!options.isEmpty()) {
            builder.append(" ");
            builder.append("[");
            var optionsToString = options.stream()
                    .map(entry -> entry.getValue().toString())
                    .collect(Collectors.joining(", "));
            builder.append(optionsToString);
            builder.append("]");
        }
        builder.append(";");
        return builder.toString();
    }

    @Override
    public ProtobufInteger index() {
        return index;
    }

    @Override
    public boolean hasIndex() {
        return index != null;
    }

    @Override
    public void setIndex(ProtobufInteger index) {
        this.index = index;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasName() {
        return name != null;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public SequencedCollection<ProtobufOptionExpression> options() {
        return Collections.unmodifiableSequencedCollection(options.sequencedValues());
    }

    @Override
    public Optional<ProtobufOptionExpression> getOption(String name) {
        return Optional.ofNullable(options.get(name));
    }

    @Override
    public void addOption(ProtobufOptionExpression value) {
        Objects.requireNonNull(value, "Cannot add null option");
        options.put(value.name().toString(), value);
    }

    @Override
    public boolean removeOption(String name) {
        return options.remove(name) != null;
    }

    @Override
    public boolean isAttributed() {
        return hasName() && hasIndex();
    }
}
