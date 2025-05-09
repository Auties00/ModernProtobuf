package it.auties.proto.features.group.conversion.map;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record MapConvertedMessageRecord(
        @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
        MapConvertedGroupRecord record
) {

}
