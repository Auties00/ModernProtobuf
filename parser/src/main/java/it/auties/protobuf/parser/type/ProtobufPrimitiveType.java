package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.ProtobufParserException;

import java.util.Objects;

public final class ProtobufPrimitiveType implements ProtobufTypeReference {
    private final ProtobufType protobufType;

    public static ProtobufPrimitiveType attributed(ProtobufType protobufType) {
        return new ProtobufPrimitiveType(protobufType);
    }

    private ProtobufPrimitiveType(ProtobufType protobufType) {
        if (protobufType.isObject() || protobufType == ProtobufType.MAP) {
            throw new ProtobufParserException("A primitive type cannot wrap an object or a map");
        }

        this.protobufType = protobufType;
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

    @Override
    public ProtobufType protobufType() {
        return protobufType;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProtobufPrimitiveType that
                && protobufType == that.protobufType;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(protobufType);
    }
}
