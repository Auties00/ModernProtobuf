package it.auties.proto.object.message.packed;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import java.util.ArrayList;

import static it.auties.protobuf.model.ProtobufType.UINT32;

@ProtobufMessage
public record PackedMessage(
        @ProtobufProperty(index = 1, type = UINT32, packed = true)
        ArrayList<Integer> content
) {

}
