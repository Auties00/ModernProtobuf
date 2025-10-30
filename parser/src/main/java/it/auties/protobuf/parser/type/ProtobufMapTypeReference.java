package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

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
public final class ProtobufMapTypeReference implements ProtobufObjectTypeReference {
    private ProtobufTypeReference key;
    private ProtobufTypeReference value;

    /**
     * Constructs a new unattributed map type reference with no key or value types.
     */
    public ProtobufMapTypeReference() {

    }

    /**
     * Constructs a new map type reference with the specified key and value types.
     *
     * @param key the key type reference
     * @param value the value type reference
     */
    public ProtobufMapTypeReference(ProtobufTypeReference key, ProtobufTypeReference value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Returns the key type of this map.
     *
     * @return the key type reference, or null if not yet set
     */
    public ProtobufTypeReference keyType() {
        return key;
    }

    /**
     * Checks whether this map type has a key type specified.
     *
     * @return {@code true} if a key type is present, {@code false} otherwise
     */
    public boolean hasKeyType() {
        return key != null;
    }

    /**
     * Sets the key type for this map.
     *
     * @param key the key type reference
     */
    public void setKeyType(ProtobufTypeReference key) {
        this.key = key;
    }

    /**
     * Returns the value type of this map.
     *
     * @return the value type reference, or null if not yet set
     */
    public ProtobufTypeReference valueType() {
        return value;
    }

    /**
     * Checks whether this map type has a value type specified.
     *
     * @return {@code true} if a value type is present, {@code false} otherwise
     */
    public boolean hasValueType() {
        return value != null;
    }

    /**
     * Sets the value type for this map.
     *
     * @param value the value type reference
     */
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
        return key != null && key.isAttributed() && value != null && value.isAttributed();
    }

    @Override
    public String toString() {
        return "map<%s, %s>".formatted(key, value);
    }
}
