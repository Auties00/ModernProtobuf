package it.auties.proto.message.repeated;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;

import java.util.ArrayList;

public record ModernBetaRepeatedMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.INT32, repeated = true)
        ArrayList<Integer> content,
        @ProtobufProperty(index = 2, type = ProtobufType.OBJECT, repeated = true)
        ArrayList<ModernRepeatedMessage> content2
) implements ProtobufMessage {

}
