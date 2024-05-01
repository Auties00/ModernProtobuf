package it.auties.protobuf.serialization.converter;

import it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public record ProtobufDeserializerElement(ExecutableElement element, TypeMirror parameterType, BuilderBehaviour behaviour) implements ProtobufConverterElement {

}
