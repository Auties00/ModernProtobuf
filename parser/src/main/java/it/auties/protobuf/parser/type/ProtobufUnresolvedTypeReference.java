package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

/**
 * Represents a reference to a user-defined type that has not yet been resolved to its declaration.
 * <p>
 * During parsing, when a field references a user-defined type (message, enum, or group), the parser
 * initially creates an unresolved type reference because the declaration may appear later in the file
 * or in another file. The semantic analyzer is responsible for resolving these unresolved references
 * to their actual declarations, converting them to {@link ProtobufMessageTypeReference},
 * {@link ProtobufEnumTypeReference}, or {@link ProtobufGroupTypeReference}.
 * </p>
 * <p>
 * Unresolved type references are never attributed ({@link #isAttributed()} always returns {@code false})
 * and have an unknown Protocol Buffer type ({@link #protobufType()} returns {@link ProtobufType#UNKNOWN}).
 * </p>
 *
 * @param name the unresolved type name as it appears in the Protocol Buffer definition, must not be null
 */
public record ProtobufUnresolvedTypeReference(String name) implements ProtobufObjectTypeReference {
    public ProtobufUnresolvedTypeReference(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.UNKNOWN;
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean isAttributed() {
        return false;
    }
}
