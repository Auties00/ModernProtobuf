package it.auties.protobuf.serialization.graph;

import it.auties.protobuf.serialization.model.ProtobufConverterMethod;

import javax.lang.model.type.TypeMirror;

// A node in the graph
record ProtobufConverterNode(
        TypeMirror from,
        TypeMirror to,
        ProtobufConverterMethod arc
) {

}
