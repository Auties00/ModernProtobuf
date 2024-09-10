package it.auties.proto.benchmark.model;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;

import java.nio.ByteBuffer;

@ProtobufMessage
public record ModernScalarMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.FIXED32)
        int fixed32,
        @ProtobufProperty(index = 2, type = ProtobufType.SFIXED32)
        int sfixed32,
        @ProtobufProperty(index = 3, type = ProtobufType.INT32)
        int int32,
        @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
        int uint32,
        @ProtobufProperty(index = 5, type = ProtobufType.FIXED64)
        long fixed64,
        @ProtobufProperty(index = 6, type = ProtobufType.SFIXED64)
        long sfixed64,
        @ProtobufProperty(index = 7, type = ProtobufType.INT64)
        long int64,
        @ProtobufProperty(index = 8, type = ProtobufType.UINT64)
        long uint64,
        @ProtobufProperty(index = 9, type = ProtobufType.FLOAT)
        float _float,
        @ProtobufProperty(index = 10, type = ProtobufType.DOUBLE)
        double _double,
        @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
        boolean bool,
        @ProtobufProperty(index = 12, type = ProtobufType.STRING)
        ProtobufString string,
        @ProtobufProperty(index = 13, type = ProtobufType.BYTES)
        ByteBuffer bytes
) {

}
