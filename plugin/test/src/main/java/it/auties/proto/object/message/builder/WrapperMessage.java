package it.auties.proto.object.message.builder;

import it.auties.protobuf.annotation.ProtobufBuilder;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import static it.auties.protobuf.model.ProtobufType.STRING;

public record WrapperMessage(
        @ProtobufProperty(index = 1, type = STRING)
        String content
) implements ProtobufMessage {
        @ProtobufBuilder(className = "SimpleWrapperMessageBuilder")
        public static WrapperMessage of(int content) {
                return new WrapperMessage(String.valueOf(content));
        }
}
