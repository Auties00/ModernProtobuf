package it.auties.protobuf.parser.typeReference;

import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

/**
 * Represents a reference to a Protocol Buffer map type.
 * <p>
 * Map types are a special construct in Protocol Buffers for representing key-value pairs.
 * They are declared using the syntax {@code map<KeyType, ValueType>} where both the key
 * and value types can be primitive or message types (with restrictions on key types).
 * </p>
 * <h2>Key Type Restrictions:</h2>
 * <p>
 * Map keys can be any integral or string type, but cannot be floating-point types or bytes.
 * Specifically, valid key types are: {@code int32}, {@code int64}, {@code uint32}, {@code uint64},
 * {@code sint32}, {@code sint64}, {@code fixed32}, {@code fixed64}, {@code sfixed32}, {@code sfixed64},
 * {@code bool}, and {@code string}.
 * </p>
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * message Example {
 *   map<string, int32> scores = 1;
 *   map<int64, Message> entities = 2;
 * }
 * }</pre>
 * <p>
 * Map type references can be created unattributed (with no key/value types) and become
 * attributed during parsing when both key and value types are specified.
 * </p>
 */
public record ProtobufMapTypeReference(ProtobufTypeReference keyType, ProtobufTypeReference valueType) implements ProtobufObjectTypeReference {
    public ProtobufMapTypeReference {
        Objects.requireNonNull(keyType, "keyType cannot be null");
        Objects.requireNonNull(valueType, "valueType cannot be null");
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.MAP;
    }

    @Override
    public String name() {
        return "map<%s, %s>".formatted(keyType, valueType);
    }

    @Override
    public boolean isAttributed() {
        return keyType.isAttributed() && valueType.isAttributed();
    }

    @Override
    public String toString() {
        return name();
    }
}
