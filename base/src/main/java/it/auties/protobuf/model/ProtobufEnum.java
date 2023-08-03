package it.auties.protobuf.model;

import it.auties.protobuf.exception.ProtobufAnnotationProcessorException;

public interface ProtobufEnum extends ProtobufObject {
    default int index() {
        throw new ProtobufAnnotationProcessorException();
    }
}
