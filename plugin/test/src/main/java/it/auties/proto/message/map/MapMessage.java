package it.auties.proto.message.map;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;

import java.util.Map;

public record MapMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.MAP, keyType = ProtobufType.STRING, valueType = ProtobufType.INT32)
        Map<String, Integer> content
) implements ProtobufMessage {

}
