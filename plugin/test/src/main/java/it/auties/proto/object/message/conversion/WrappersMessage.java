package it.auties.proto.object.message.conversion;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import java.util.ArrayList;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record WrappersMessage(
        @ProtobufProperty(index = 1, type = STRING)
        ArrayList<Wrapper> wrappers
) {

}
