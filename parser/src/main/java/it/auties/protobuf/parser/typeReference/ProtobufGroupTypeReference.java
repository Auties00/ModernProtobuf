package it.auties.protobuf.parser.typeReference;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufGroupStatement;

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
 * @see ProtobufGroupStatement
 */
public record ProtobufGroupTypeReference(ProtobufGroupStatement declaration) implements ProtobufObjectTypeReference {
    public ProtobufGroupTypeReference {
        Objects.requireNonNull(declaration, "declaration cannot be null");
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.GROUP;
    }

    @Override
    public String name() {
        return declaration.name();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean isAttributed(){
        return true;
    }
}
