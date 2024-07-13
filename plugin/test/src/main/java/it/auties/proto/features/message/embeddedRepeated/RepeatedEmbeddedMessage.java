package it.auties.proto.features.message.embeddedRepeated;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.ArrayList;

@ProtobufMessage
public record RepeatedEmbeddedMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.INT32)
        ArrayList<Integer> content
) {

}
