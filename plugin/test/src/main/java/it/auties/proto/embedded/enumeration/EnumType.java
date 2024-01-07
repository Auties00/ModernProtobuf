package it.auties.proto.embedded.enumeration;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.model.ProtobufEnum;

public enum EnumType implements ProtobufEnum {
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
