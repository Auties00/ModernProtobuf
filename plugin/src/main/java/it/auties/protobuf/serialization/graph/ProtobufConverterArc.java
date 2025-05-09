package it.auties.protobuf.serialization.graph;

import it.auties.protobuf.serialization.model.converter.ProtobufConverterMethod;

import javax.lang.model.type.TypeMirror;

public record ProtobufConverterArc(ProtobufConverterMethod method, TypeMirror returnType, String warning) {

}
