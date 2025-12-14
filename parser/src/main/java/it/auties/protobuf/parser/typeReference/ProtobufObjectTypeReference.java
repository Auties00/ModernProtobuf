package it.auties.protobuf.parser.typeReference;

/**
 * Represents a reference to an object (non-primitive) type in a Protocol Buffer definition.
 * <p>
 * Object type references include user-defined types (messages, enums, groups) and the special
 * {@code map} type. Unlike primitive types, object types require declarations and may need to
 * be resolved during semantic analysis.
 * </p>
 * <p>
 * This sealed interface permits the following implementations:
 * </p>
 * <ul>
 *   <li>{@link ProtobufUnresolvedTypeReference} - A type reference that has not yet been resolved to its declaration</li>
 *   <li>{@link ProtobufMessageTypeReference} - A reference to a message type</li>
 *   <li>{@link ProtobufEnumTypeReference} - A reference to an enum type</li>
 *   <li>{@link ProtobufGroupTypeReference} - A reference to a group type (deprecated in Protocol Buffers)</li>
 *   <li>{@link ProtobufMapTypeReference} - A reference to a map type with key and value types</li>
 * </ul>
 *
 * @see ProtobufUnresolvedTypeReference
 * @see ProtobufMessageTypeReference
 * @see ProtobufEnumTypeReference
 * @see ProtobufGroupTypeReference
 * @see ProtobufMapTypeReference
 */
public sealed interface ProtobufObjectTypeReference
        extends ProtobufTypeReference
        permits ProtobufUnresolvedTypeReference, ProtobufMessageTypeReference, ProtobufEnumTypeReference, ProtobufGroupTypeReference, ProtobufMapTypeReference {

}
