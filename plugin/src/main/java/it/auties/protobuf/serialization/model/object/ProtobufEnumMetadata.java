package it.auties.protobuf.serialization.model.object;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

public record ProtobufEnumMetadata(ExecutableElement constructor, VariableElement field, VariableElement parameter, int parameterIndex) {
    private static final ProtobufEnumMetadata UNKNOWN = new ProtobufEnumMetadata(null, null, null, -1);
    private static final ProtobufEnumMetadata JAVA = new ProtobufEnumMetadata(null, null, null, -2);

    public static ProtobufEnumMetadata unknown() {
        return UNKNOWN;
    }

    public static ProtobufEnumMetadata javaEnum() {
        return JAVA;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    public boolean isJavaEnum() {
        return this == JAVA;
    }
}
