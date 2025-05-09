package it.auties.proto.features.group.conversion.nested;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record NestedConvertedMessageRecord(
        @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
        NestedConvertedGroupRecord record
) {

}
