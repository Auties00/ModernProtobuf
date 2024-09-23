package it.auties.proto.features.group.nested;

import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;

@ProtobufGroup
public record NestedGroupRecord(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        ProtobufString string,
        @ProtobufProperty(index = 2, type = ProtobufType.GROUP)
        NestedGroupRecord record
) {

}
