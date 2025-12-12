package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufEnumStatement;

import java.util.Objects;

/**
 * Represents a resolved reference to a Protocol Buffer enum type.
 * <p>
 * Enum type references are created during semantic analysis when an {@link ProtobufUnresolvedTypeReference}
 * is resolved to an enum declaration. This type reference maintains a link to the actual enum
 * declaration, allowing access to the enum's constants and metadata.
 * </p>
 * <p>
 * Enum type references are attributed when they contain a non-null reference to the declaration.
 * </p>
 *
 * @param declaration the enum declaration statement, must not be null
 */
public record ProtobufEnumTypeReference(ProtobufEnumStatement declaration) implements ProtobufObjectTypeReference {
    public ProtobufEnumTypeReference {
        Objects.requireNonNull(declaration, "declaration cannot be null");
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.ENUM;
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
        return declaration != null;
    }
}
