package it.auties.proto.features.message.repeated;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.ArrayList;

@ProtobufMessage
public record ModernBetaRepeatedMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.INT32)
        ArrayList<Integer> content,
        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        ArrayList<ModernRepeatedMessage> content2
) {

}
