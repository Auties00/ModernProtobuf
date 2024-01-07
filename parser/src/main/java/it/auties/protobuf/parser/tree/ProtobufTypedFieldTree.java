package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Optional;

public abstract sealed class ProtobufTypedFieldTree extends ProtobufFieldTree permits ProtobufModifiableFieldTree {
    ProtobufTypeReference type;

    public Optional<ProtobufTypeReference> type() {
        return Optional.ofNullable(type);
    }

    public ProtobufTypedFieldTree setType(ProtobufTypeReference type) {
        this.type = type;
        return this;
    }

    @Override
    public boolean isAttributed() {
        return super.isAttributed() && type != null;
    }
}
