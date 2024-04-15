package it.auties.proto.object.message.packed;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import java.util.ArrayList;

import static it.auties.protobuf.model.ProtobufType.UINT32;

public record PackedMessage(
        @ProtobufProperty(index = 1, type = UINT32, packed = true)
        ArrayList<Integer> content
) implements ProtobufMessage {

}
