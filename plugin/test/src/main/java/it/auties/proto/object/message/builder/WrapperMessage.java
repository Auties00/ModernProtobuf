package it.auties.proto.object.message.builder;

import it.auties.protobuf.annotation.ProtobufBuilder;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record WrapperMessage(
        @ProtobufProperty(index = 1, type = STRING)
        String content
) {
    @ProtobufBuilder(className = "ConstructorWrapperMessageBuilder")
    public WrapperMessage(int content) {
        this(String.valueOf(content));
    }

    @ProtobufBuilder(className = "StaticWrapperMessageBuilder")
    public static WrapperMessage of(int content) {
        return new WrapperMessage(String.valueOf(content));
    }
}
