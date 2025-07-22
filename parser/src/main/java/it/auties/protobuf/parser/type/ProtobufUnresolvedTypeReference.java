package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufEnumStatement;
import it.auties.protobuf.parser.tree.ProtobufTree;

import java.util.Objects;

public record ProtobufUnresolvedTypeReference(String name) implements ProtobufTypeReference {
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
