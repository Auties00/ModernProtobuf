package it.auties.proto.features.enumeration.defaultValue;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum
public enum EnumType {
    FIRST(0),
    SECOND(1),
    THIRD(3),
    @ProtobufDefaultValue
    DEFAULT_VALUE(19431);

    final int index;

    EnumType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}
