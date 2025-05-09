package it.auties.proto.features.group.conversion.repeated;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record RepeatedConvertedMessageRecord(
        @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
        RepeatedConvertedGroupRecord record
) {

}
