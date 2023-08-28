package it.auties.proto.embedded.enumeration;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.model.ProtobufEnum;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public enum EnumType implements ProtobufEnum {
    FIRST(0),
    SECOND(1),
    THIRD(10);

    @ProtobufEnumIndex
    final int index;

    EnumType(@ProtobufEnumIndex int index) {
        this.index = index;
    }
}
