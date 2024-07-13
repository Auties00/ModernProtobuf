package it.auties.proto.features.message.embeddedRepeated;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.ArrayList;

@ProtobufMessage
public record RepeatedWrapperMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.OBJECT)
        ArrayList<RepeatedEmbeddedWrapperMessage> content
) {

}
