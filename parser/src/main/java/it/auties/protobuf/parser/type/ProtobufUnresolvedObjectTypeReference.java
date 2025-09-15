package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

public record ProtobufUnresolvedObjectTypeReference(String name) implements ProtobufObjectTypeReference {
    public ProtobufUnresolvedObjectTypeReference(String name) {
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
