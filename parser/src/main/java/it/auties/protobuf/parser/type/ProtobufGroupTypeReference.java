package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufGroupFieldStatement;

import java.util.Objects;

/**
 * Represents a reference to a Protocol Buffer group type.
 * <p>
 * Groups are a deprecated feature from Protocol Buffers 2 that combine a message definition
 * with a field declaration. While groups are still supported for backward compatibility,
 * they should not be used in new Protocol Buffer definitions. Use nested messages instead.
 * </p>
 * <p>
 * Group type references can start unattributed (when created with just a name) and become
 * attributed when linked to their declaration during semantic analysis.
 * </p>
 * <p>
 * <strong>Note:</strong> Groups are deprecated in Protocol Buffers and should not be used in new code.
 * </p>
 *
 * @see ProtobufGroupFieldStatement
 */
public final class ProtobufGroupTypeReference implements ProtobufObjectTypeReference {
    private final String name;
    private ProtobufGroupFieldStatement declaration;

    /**
     * Constructs a new unattributed group type reference with the specified name.
     *
     * @param name the group type name, must not be null
     */
    public ProtobufGroupTypeReference(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    /**
     * Constructs a new attributed group type reference linked to its declaration.
     *
     * @param declaration the group field declaration statement, must not be null
     */
    public ProtobufGroupTypeReference(ProtobufGroupFieldStatement declaration) {
        Objects.requireNonNull(declaration, "declaration cannot be null");
        this.name = declaration.name();
        this.declaration = declaration;
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.GROUP;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the group field declaration statement if this reference has been attributed.
     *
     * @return the declaration, or null if not yet attributed
     */
    public ProtobufGroupFieldStatement declaration() {
        return declaration;
    }

    /**
     * Checks whether this group type reference has been attributed with a declaration.
     *
     * @return {@code true} if a declaration is present, {@code false} otherwise
     */
    public boolean hasDeclaration() {
        return declaration != null;
    }

    /**
     * Sets the declaration for this group type reference, attributing it.
     *
     * @param statement the group field declaration statement
     */
    public void setDeclaration(ProtobufGroupFieldStatement statement) {
        this.declaration = statement;
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean isAttributed(){
        return declaration != null;
    }
}
