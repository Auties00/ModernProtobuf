package it.auties.protobuf.serialization.converter;

import javax.lang.model.element.ExecutableElement;

public record ProtobufDeserializerElement(ExecutableElement element) implements ProtobufConverterElement {

}
