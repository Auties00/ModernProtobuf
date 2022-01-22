package it.auties.protobuf.decoder;

import java.util.Map;

public interface ProtobufTypeDescriptor {
    Map<Integer, Class<?>> descriptor();
}
