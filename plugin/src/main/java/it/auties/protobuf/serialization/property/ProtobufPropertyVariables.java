package it.auties.protobuf.serialization.property;

import javax.lang.model.type.TypeMirror;
import java.util.List;

public record ProtobufPropertyVariables(boolean hasConverter, List<ProtobufPropertyVariable> variables) {
    public record ProtobufPropertyVariable(TypeMirror type, String name, String value, boolean primitive) {

    }
}