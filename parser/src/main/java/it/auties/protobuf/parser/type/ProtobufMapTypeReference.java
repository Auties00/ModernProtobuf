package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

public final class ProtobufMapTypeReference implements ProtobufObjectTypeReference {

    private ProtobufTypeReference key;
    private ProtobufTypeReference value;

    public ProtobufMapTypeReference() {

    }

    public ProtobufMapTypeReference(ProtobufTypeReference key, ProtobufTypeReference value) {
        this.key = key;
        this.value = value;
    }

    public ProtobufTypeReference keyType() {
        return key;
    }

    public boolean hasKeyType() {
        return key != null;
    }

    public void setKeyType(ProtobufTypeReference key) {
        this.key = key;
    }

    public ProtobufTypeReference valueType() {
        return value;
    }

    public boolean hasValueType() {
        return value != null;
    }

    public void setValueType(ProtobufTypeReference value) {
        this.value = value;
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.MAP;
    }

    @Override
    public String name() {
        return "map";
    }

    @Override
    public boolean isAttributed() {
        return key != null && value != null;
    }

    @Override
    public String toString() {
        return "map<%s, %s>".formatted(key, value);
    }
}
