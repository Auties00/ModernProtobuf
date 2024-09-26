package it.auties.protobuf.serialization.model.property;

public record ProtobufGroupPropertyElement(
        int index,
        ProtobufPropertyType type,
        boolean packed
) {

}
