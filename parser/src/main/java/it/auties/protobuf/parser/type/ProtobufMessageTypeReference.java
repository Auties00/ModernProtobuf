package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufMessageStatement;

import java.util.Objects;

/**
 * Represents a resolved reference to a Protocol Buffer message type.
 * <p>
 * Message type references are created during semantic analysis when an {@link ProtobufUnresolvedTypeReference}
 * is resolved to a message declaration. This type reference maintains a link to the actual message
 * declaration, allowing access to the message's structure and metadata.
 * </p>
 * <p>
 * Message type references are always attributed since they contain a reference to the resolved declaration.
 * </p>
 *
 * @param declaration the message declaration statement, must not be null
 */
public record ProtobufMessageTypeReference(ProtobufMessageStatement declaration) implements ProtobufObjectTypeReference {
    public ProtobufMessageTypeReference {
        Objects.requireNonNull(declaration, "declaration cannot be null");
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.MESSAGE;
    }

    @Override
    public String name() {
        return declaration.qualifiedName();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean isAttributed() {
        return true;
    }
}
