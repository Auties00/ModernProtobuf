package it.auties.proto.features.message.builder;

import it.auties.protobuf.annotation.ProtobufBuilder;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;

import static it.auties.protobuf.model.ProtobufType.STRING;

@ProtobufMessage
public record WrapperMessage(
        @ProtobufProperty(index = 1, type = STRING)
        ProtobufString content
) {
    @ProtobufBuilder(className = "ConstructorWrapperMessageBuilder")
    public WrapperMessage(int content) {
        this(ProtobufString.wrap(String.valueOf(content)));
    }

    @ProtobufBuilder(className = "StaticWrapperMessageBuilder")
    public static WrapperMessage of(int content) {
        return new WrapperMessage(ProtobufString.wrap(String.valueOf(content)));
    }
}
