package it.auties.proto.features.message.map;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Map;

@ProtobufMessage
public record MapMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.INT32)
        Map<String, Integer> content
) {

}
