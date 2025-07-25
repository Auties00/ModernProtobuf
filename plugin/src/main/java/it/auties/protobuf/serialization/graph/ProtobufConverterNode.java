package it.auties.protobuf.serialization.graph;

import it.auties.protobuf.serialization.model.ProtobufConverterMethod;

import javax.lang.model.type.TypeMirror;
import java.util.Objects;

// A node in the graph
record ProtobufConverterNode(
        TypeMirror from,
        TypeMirror to,
        ProtobufConverterMethod arc,
        String warning
) {
    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtobufConverterNode that
                && Objects.equals(from.toString(), that.from().toString())
                && Objects.equals(to.toString(), that.to().toString())
                && Objects.equals(arc, that.arc());
    }

    @Override
    public int hashCode() {
        return Objects.hash(from.toString(), to.toString(), arc);
    }
}
