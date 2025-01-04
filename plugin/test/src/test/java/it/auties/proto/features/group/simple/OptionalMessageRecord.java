package it.auties.proto.features.group.simple;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage
public record OptionalMessageRecord(
        @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
        Optional<GroupRecord> record
) {

}
