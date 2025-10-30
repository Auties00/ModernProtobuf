package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufTypeReference;

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
 * During semantic analysis, the enum constant name is resolved to its type reference,
 * linking it to the actual enum declaration.
 * </p>
 *
 * @see ProtobufExpression
 * @see ProtobufEnumStatement
 */
public final class ProtobufEnumConstantExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    private String name;
    private ProtobufTypeReference type;

    /**
     * Constructs a new enum constant expression at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufEnumConstantExpression(int line) {
        super(line);
    }

    /**
     * Returns the name of the enum constant.
     *
     * @return the constant name, or null if not yet set
     */
    public String name() {
        return name;
    }

    /**
     * Checks whether this expression has a name assigned.
     *
     * @return true if a name is present, false otherwise
     */
    public boolean hasName() {
        return name != null;
    }

    /**
     * Sets the name of the enum constant.
     *
     * @param name the constant name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the type reference to the enum containing this constant.
     * <p>
     * This is populated during semantic analysis after type resolution.
     * </p>
     *
     * @return the enum type reference, or null if not yet attributed
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
     * Sets the type reference for this enum constant.
     *
     * @param type the enum type reference
     */
    public void setType(ProtobufTypeReference type) {
        this.type = type;
    }

    @Override
    public boolean isAttributed() {
        return name != null && type != null;
    }

    @Override
    public String toString() {
        return name;
    }
}
