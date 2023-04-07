package it.auties.protobuf.serialization.performance.model;

import lombok.NonNull;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;

public record ProtobufTypeImplementation(@NonNull TypeMirror rawType, TypeElement rawElement, TypeMirror parameterType, TypeElement parameterElement) {
    public TypeMirror parameterType(){
        return Objects.requireNonNullElse(parameterType, rawType);
    }

    public TypeElement parameterElement(){
        return parameterElement != null ? parameterElement : rawElement;
    }
}
