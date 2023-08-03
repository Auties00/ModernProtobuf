package it.auties.protobuf.model;

import it.auties.protobuf.exception.ProtobufAnnotationProcessorException;

public interface ProtobufMessage extends ProtobufObject {
    default byte[] toEncodedProtobuf(@SuppressWarnings("unused") ProtobufVersion version) {
        throw new ProtobufAnnotationProcessorException();
    }
}
