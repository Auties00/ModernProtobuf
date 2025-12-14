package it.auties.protobuf.parser.typeReference;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.exception.ProtobufParserException;

import java.util.Objects;

/**
 * Represents a reference to a primitive (scalar) type in a Protocol Buffer definition.
 * <p>
 * Primitive type references correspond to the built-in Protocol Buffer scalar types such as
 * {@code int32}, {@code string}, {@code bool}, {@code bytes}, etc. These types are defined by
 * the Protocol Buffer language specification and do not require user declarations.
 * </p>
 * <p>
 * Primitive type references are always attributed since they are resolved at parse time.
 * </p>
 * <h2>Supported Primitive Types:</h2>
 * <ul>
 *   <li>Integer types: {@code int32}, {@code int64}, {@code uint32}, {@code uint64},
 *       {@code sint32}, {@code sint64}, {@code fixed32}, {@code fixed64}, {@code sfixed32}, {@code sfixed64}</li>
 *   <li>Floating-point types: {@code float}, {@code double}</li>
 *   <li>Boolean type: {@code bool}</li>
 *   <li>String and bytes: {@code string}, {@code bytes}</li>
 * </ul>
 *
 * @param protobufType the primitive Protocol Buffer type, must not be null and must be a primitive type
 */
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
