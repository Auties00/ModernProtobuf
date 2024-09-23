package it.auties.proto.features.group.simple;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record MessageRecord(
        @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
        GroupRecord record
) {

}
