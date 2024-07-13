package it.auties.proto.features.message.mixin.future.object;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage
public record VersionMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.INT32)
        int major,
        @ProtobufProperty(index = 2, type = ProtobufType.INT32)
        int minor
) {

}
