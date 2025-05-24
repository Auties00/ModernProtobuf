package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufMapType implements ProtobufTypeReference {
    private static final String NAME = "map";

    private ProtobufTypeReference key;
    private ProtobufTypeReference value;
    private ProtobufMapType(ProtobufTypeReference key, ProtobufTypeReference value) {
        this.key = key;
        this.value = value;
    }

    public static ProtobufTypeReference unattributed() {
        return new ProtobufMapType(null, null);
    }

    public static ProtobufTypeReference attributed(ProtobufTypeReference keyType, ProtobufTypeReference valueType) {
        return new ProtobufMapType(Objects.requireNonNull(keyType), Objects.requireNonNull(valueType));
    }

    public Optional<ProtobufTypeReference> keyType() {
        return Optional.ofNullable(key);
    }

    public void setKeyType(ProtobufTypeReference key) {
        this.key = key;
    }

    public Optional<ProtobufTypeReference> valueType() {
        return Optional.ofNullable(value);
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
        return NAME;
    }

    @Override
    public boolean isAttributed() {
        return key != null && value != null;
    }

    @Override
    public String toString() {
        return "%s<%s, %s>".formatted(NAME, key, value);
    }
}
