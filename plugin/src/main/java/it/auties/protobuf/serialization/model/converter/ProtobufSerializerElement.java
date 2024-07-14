package it.auties.protobuf.serialization.model.converter;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public record ProtobufSerializerElement(ExecutableElement delegate, TypeMirror returnType) implements ProtobufConverterElement {

}
