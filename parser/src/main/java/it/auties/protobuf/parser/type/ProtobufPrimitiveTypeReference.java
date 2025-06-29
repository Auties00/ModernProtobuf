package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.ProtobufParserException;

import java.util.Objects;

public record ProtobufPrimitiveTypeReference(ProtobufType protobufType) implements ProtobufTypeReference {
    public ProtobufPrimitiveTypeReference {
        Objects.requireNonNull(protobufType, "protobufType cannot be null");
        if (!protobufType.isPrimitive()) {
            throw new ProtobufParserException(protobufType.name() + " is not a primitive type");
        }
    }

    @Override
    public boolean isAttributed() {
        return true;
    }

    @Override
    public String name() {
        return protobufType.name().toLowerCase();
    }

    @Override
    public String toString() {
        return name();
    }
}
