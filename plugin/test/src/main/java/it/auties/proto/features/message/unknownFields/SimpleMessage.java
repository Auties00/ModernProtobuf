package it.auties.proto.features.message.unknownFields;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.annotation.ProtobufUnknownFields;
import it.auties.protobuf.model.ProtobufType;

import java.util.Map;

@ProtobufMessage
public record SimpleMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String value,
        @ProtobufUnknownFields
        Map<Integer, Object> unknownFields
) {

}
