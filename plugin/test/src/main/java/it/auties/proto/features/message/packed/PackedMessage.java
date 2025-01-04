package it.auties.proto.features.message.packed;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.annotation.ProtobufReservedRange;

import java.util.ArrayList;

import static it.auties.protobuf.model.ProtobufType.UINT32;

@ProtobufMessage(
        reservedIndexes = {11},
        reservedRanges = {
                @ProtobufReservedRange(min = 2, max = 10)
        }
)
public record PackedMessage(
        @ProtobufProperty(index = 1, type = UINT32, packed = true)
        ArrayList<Integer> content
) {

}
