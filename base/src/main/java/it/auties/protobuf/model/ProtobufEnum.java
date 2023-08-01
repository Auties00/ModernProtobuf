package it.auties.protobuf.model;

public interface ProtobufEnum extends ProtobufObject {
    default int index() {
        throw new UnsupportedOperationException("stub");
    }
}
