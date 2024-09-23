package it.auties.protobuf.serialization.model.converter;

import it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public record ProtobufDeserializerElement(ExecutableElement delegate, TypeMirror parameterType, BuilderBehaviour behaviour) implements ProtobufConverterElement {

    public TypeMirror returnType() {
        if(delegate.getKind() == ElementKind.CONSTRUCTOR) {
            return delegate.getEnclosingElement().asType();
        }

        return delegate.getReturnType();
    }
}
