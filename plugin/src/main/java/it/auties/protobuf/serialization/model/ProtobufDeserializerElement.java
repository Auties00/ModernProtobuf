package it.auties.protobuf.serialization.model;

import javax.lang.model.element.ExecutableElement;

public record ProtobufDeserializerElement(ExecutableElement element) implements ProtobufConverterElement {
}
