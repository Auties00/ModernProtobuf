package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufEnumStatement;
import it.auties.protobuf.parser.tree.ProtobufMessageStatement;
import it.auties.protobuf.parser.tree.ProtobufTree;

import java.util.Objects;

public record ProtobufMessageTypeReference(ProtobufMessageStatement declaration) implements ProtobufTypeReference {
    public ProtobufMessageTypeReference {
        Objects.requireNonNull(declaration, "declaration cannot be null");
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.MESSAGE;
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
    public boolean isAttributed() {
        return true;
    }
}
