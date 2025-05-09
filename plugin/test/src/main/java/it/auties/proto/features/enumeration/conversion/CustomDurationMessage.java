package it.auties.proto.features.enumeration.conversion;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record CustomDurationMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
        CustomDuration duration
) {

}
