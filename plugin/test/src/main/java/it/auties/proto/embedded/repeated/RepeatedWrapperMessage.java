package it.auties.proto.embedded.repeated;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;

import java.util.ArrayList;

public record RepeatedWrapperMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.OBJECT, repeated = true)
        ArrayList<RepeatedEmbeddedWrapperMessage> content
) implements ProtobufMessage {
}