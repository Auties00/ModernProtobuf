package it.auties.protobuf.serialization.graph;

import it.auties.protobuf.serialization.model.ProtobufConverterMethod;

import javax.lang.model.type.TypeMirror;

public record ProtobufConverterArc(
        ProtobufConverterMethod method,
        TypeMirror returnType
) {

}
