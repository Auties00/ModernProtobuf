package it.auties.proto.object.message.conversion;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import java.util.ArrayList;

import static it.auties.protobuf.model.ProtobufType.STRING;

public record WrappersMessage(
        @ProtobufProperty(index = 1, type = STRING)
        ArrayList<Wrapper> wrappers
) implements ProtobufMessage {

}
