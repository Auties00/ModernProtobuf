package it.auties.protobuf.parser.typeReference;

import it.auties.protobuf.model.ProtobufType;

/**
 * Represents a reference to a type in a Protocol Buffer definition.
 * <p>
 * Type references appear in field declarations, method parameters, and return types throughout
 * Protocol Buffer definitions. This sealed interface provides a unified representation of both
 * primitive types (scalar types like {@code int32}, {@code string}) and object types (messages,
 * enums, groups, and maps).
 * </p>
 * <p>
 * Type references can be in one of two states:
 * </p>
 * <ul>
 *   <li><strong>Attributed:</strong> The type reference has been resolved and linked to its definition.
 *       This is indicated by {@link #isAttributed()} returning {@code true}.</li>
 *   <li><strong>Unattributed:</strong> The type reference has not yet been resolved, typically because
 *       it refers to a user-defined type that will be resolved during semantic analysis.</li>
 * </ul>
 *
 * @see ProtobufPrimitiveTypeReference
 * @see ProtobufObjectTypeReference
 */
public sealed interface ProtobufTypeReference
        permits ProtobufPrimitiveTypeReference, ProtobufObjectTypeReference {
    /**
     * Returns the name of this type.
     *
     * @return the type name as it appears in the Protocol Buffer definition
     */
    String name();

    /**
     * Returns the Protocol Buffer type category this reference represents.
     *
     * @return the {@link ProtobufType} enum value for this type
     */
    ProtobufType protobufType();

    /**
     * Indicates whether this type reference has been attributed (resolved and linked to its definition).
     * <p>
     * Primitive types are always attributed. Object types may be unattributed initially and become
     * attributed during semantic analysis when their declarations are resolved.
     * </p>
     *
     * @return {@code true} if this type reference is attributed, {@code false} otherwise
     */
    boolean isAttributed();
}
