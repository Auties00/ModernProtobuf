package it.auties.protobuf.serialization.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

public record ProtobufEnumMetadata(ExecutableElement constructor, VariableElement field, VariableElement parameter, int parameterIndex) {
    public static ProtobufEnumMetadata unknown() {
        return new ProtobufEnumMetadata(null, null, null, -1);
    }

    public boolean isUnknown() {
        return parameterIndex == -1;
    }
}
