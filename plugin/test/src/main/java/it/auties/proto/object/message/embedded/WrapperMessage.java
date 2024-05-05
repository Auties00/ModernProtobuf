package it.auties.proto.object.message.embedded;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import static it.auties.protobuf.model.ProtobufType.OBJECT;

@ProtobufMessage
public record WrapperMessage(
        @ProtobufProperty(index = 1, type = OBJECT)
        EmbeddedMessage content
) {

}
