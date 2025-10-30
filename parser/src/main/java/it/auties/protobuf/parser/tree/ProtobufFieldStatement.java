package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufInteger;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a field declaration in the Protocol Buffer AST.
 * <p>
 * Fields are the primary data members of messages, specifying the structure and types of data
 * that can be stored. Each field has a name, type, field number (index), an optional modifier
 * (required/optional/repeated), and optional field options.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * // Simple fields
 * string name = 1;
 * int32 age = 2;
 *
 * // Fields with modifiers (proto2)
 * required string email = 3;
 * optional int32 phone = 4;
 * repeated string tags = 5;
 *
 * // Fields with options
 * string id = 1 [deprecated = true];
 * bytes data = 2 [ctype = CORD, packed = true];
 *
 * // Complex type fields
 * User user = 10;
 * map<string, int32> scores = 11;
 * }</pre>
 * <p>
 * Field modifiers (proto2 vs proto3):
 * </p>
 * <ul>
 *   <li><strong>required:</strong> (proto2 only) Field must be set</li>
 *   <li><strong>optional:</strong> Field may or may not be set</li>
 *   <li><strong>repeated:</strong> Field can appear zero or more times (array/list)</li>
 *   <li><strong>none:</strong> Default for proto3 fields (implicitly optional)</li>
 * </ul>
 * <p>
 * Field numbers (indices) must be:
 * </p>
 * <ul>
 *   <li>Unique within the message</li>
 *   <li>In the range 1 to 536,870,911 (2^29 - 1)</li>
 *   <li>Not in the reserved range 19,000 to 19,999 (used by Protocol Buffers implementation)</li>
 *   <li>Not previously reserved by the message</li>
 * </ul>
 * <p>
 * This sealed class permits three specialized subclasses:
 * </p>
 * <ul>
 *   <li>{@link ProtobufEnumConstantStatement}: Enum value declarations</li>
 *   <li>{@link ProtobufGroupFieldStatement}: Deprecated group field syntax</li>
 *   <li>{@link ProtobufOneofFieldStatement}: Oneof group declarations</li>
 * </ul>
 * <p>
 * This class implements multiple child marker interfaces, allowing fields to appear in messages,
 * oneofs, extend blocks, and groups. Fields are attributed when they have a name, type, index,
 * and modifier assigned.
 * </p>
 *
 * @see ProtobufMessageChild
 * @see ProtobufOneofChild
 * @see ProtobufTypeReference
 * @see ProtobufOptionExpression
 */
public sealed class ProtobufFieldStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithIndex, ProtobufTree.WithOptions,
                   ProtobufOneofChild, ProtobufMessageChild, ProtobufGroupChild, ProtobufExtendChild
        permits ProtobufEnumConstantStatement, ProtobufGroupFieldStatement, ProtobufOneofFieldStatement {
    protected Modifier modifier;
    protected ProtobufTypeReference type;
    protected String name;
    protected ProtobufInteger index;
    protected final SequencedMap<String, ProtobufOptionExpression> options;

    /**
     * Constructs a new field statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufFieldStatement(int line) {
        super(line);
        this.options = new LinkedHashMap<>();
    }

    /**
     * Returns the field name.
     *
     * @return the field name, or null if not yet set
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Checks whether this field has a name assigned.
     *
     * @return true if a name is present, false otherwise
     */
    @Override
    public boolean hasName() {
        return name != null;
    }

    /**
     * Sets the name for this field.
     * <p>
     * The name must be a valid Protocol Buffer identifier and unique within the parent scope.
     * </p>
     *
     * @param name the field name to set
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the field number (index).
     * <p>
     * Field numbers are used to identify fields in the binary encoding and must be unique
     * within the message.
     * </p>
     *
     * @return the field number, or null if not yet set
     */
    @Override
    public ProtobufInteger index() {
        return index;
    }

    /**
     * Checks whether this field has an index assigned.
     *
     * @return true if an index is present, false otherwise
     */
    @Override
    public boolean hasIndex() {
        return index != null;
    }

    /**
     * Sets the field number (index) for this field.
     * <p>
     * The index must be within valid ranges and not conflict with reserved numbers.
     * </p>
     *
     * @param index the field number to set
     */
    @Override
    public void setIndex(ProtobufInteger index) {
        this.index = index;
    }

    /**
     * Returns the field type reference.
     *
     * @return the type reference, or null if not yet set
     */
    public ProtobufTypeReference type() {
        return type;
    }

    /**
     * Checks whether this field has a type assigned.
     *
     * @return true if a type is present, false otherwise
     */
    public boolean hasType() {
        return type != null;
    }

    /**
     * Sets the type reference for this field.
     * <p>
     * The type can be a primitive type, message type, enum type, or map type.
     * </p>
     *
     * @param type the type reference to set
     */
    public void setType(ProtobufTypeReference type) {
        this.type = type;
    }

    /**
     * Returns the field modifier (required/optional/repeated/none).
     *
     * @return the modifier, or null if not yet set
     */
    public Modifier modifier() {
        return modifier;
    }

    /**
     * Checks whether this field has a modifier assigned.
     *
     * @return true if a modifier is present, false otherwise
     */
    public boolean hasModifier() {
        return modifier != null;
    }

    /**
     * Sets the modifier for this field.
     *
     * @param modifier the modifier to set
     */
    public void setModifier(Modifier modifier) {
        this.modifier = modifier;
    }

    @Override
    public SequencedCollection<ProtobufOptionExpression> options() {
        return options.sequencedValues();
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
    public Optional<ProtobufOptionExpression> getOption(String name) {
        return Optional.ofNullable(options.get(name));
    }

    @Override
    public boolean isAttributed() {
        return hasIndex() && hasName() && hasModifier() && hasType();
    }

    /**
     * Helper method to write field options to a string builder.
     * <p>
     * Options are written in square brackets as comma-separated key-value pairs.
     * </p>
     *
     * @param builder the string builder to write to
     */
    void writeOptions(StringBuilder builder) {
        var options = this.options.sequencedEntrySet();
        if (options.isEmpty()) {
            return;
        }

        builder.append(" ");
        builder.append("[");
        var optionsToString = options.stream()
                .map(entry -> entry.getValue().toString())
                .collect(Collectors.joining(", "));
        builder.append(optionsToString);
        builder.append("]");
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        if(modifier != null && modifier != Modifier.NONE) {
            builder.append(modifier.token());
            builder.append(" ");
        }
        var type = Objects.requireNonNullElse(this.type.toString(), "[missing]");
        builder.append(type);
        builder.append(" ");
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");
        var index = Objects.requireNonNullElse(this.index, "[missing]");
        builder.append("=");
        builder.append(" ");
        builder.append(index);
        writeOptions(builder);
        builder.append(";");
        return builder.toString();
    }

    /**
     * Enumeration of field modifiers that control field cardinality and requirement semantics.
     */
    public enum Modifier {
        /**
         * No explicit modifier - default for proto3 fields (implicitly optional).
         */
        NONE(""),

        /**
         * Required modifier (proto2 only) - field must be set.
         * <p>
         * Required fields must have a value when serializing a message. This modifier
         * is not available in proto3.
         * </p>
         */
        REQUIRED("required"),

        /**
         * Optional modifier - field may or may not be set.
         * <p>
         * Optional fields can be omitted from messages. In proto3, this is the default
         * for singular fields.
         * </p>
         */
        OPTIONAL("optional"),

        /**
         * Repeated modifier - field can appear zero or more times (array/list).
         * <p>
         * Repeated fields represent collections of values of the same type.
         * </p>
         */
        REPEATED("repeated");

        private final String token;

        Modifier(String token) {
            this.token = token;
        }

        /**
         * Returns the keyword token for this modifier.
         *
         * @return the token string
         */
        public String token() {
            return token;
        }

        private static final Map<String, Modifier> VALUES = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Modifier::token, Function.identity()));

        /**
         * Looks up a modifier by its token string.
         *
         * @param name the token string
         * @return optional containing the modifier, or empty if not found
         */
        public static Optional<Modifier> of(String name) {
            return Optional.ofNullable(VALUES.get(name));
        }
    }
}
