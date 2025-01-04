package it.auties.proto.features.message.repeated;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.ArrayList;

@ProtobufMessage
public record ModernRepeatedMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.INT32)
        ArrayList<Integer> repeatedData
) {

}
