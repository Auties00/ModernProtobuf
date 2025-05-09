package it.auties.proto.features.group.conversion.simple;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record ConvertedMessageRecord(
        @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
        ConvertedGroupRecord record
) {

}
