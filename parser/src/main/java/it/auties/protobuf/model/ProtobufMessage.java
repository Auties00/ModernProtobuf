package it.auties.protobuf.model;

import java.util.Map;

public interface ProtobufMessage {
    Map<Integer, Class<?>> types();
}
