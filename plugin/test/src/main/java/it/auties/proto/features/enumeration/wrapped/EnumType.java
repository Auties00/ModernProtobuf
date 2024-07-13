package it.auties.proto.features.enumeration.wrapped;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum
public enum EnumType {
    FIRST(0),
    SECOND(1),
    THIRD(10);

    final int index;

    EnumType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}
