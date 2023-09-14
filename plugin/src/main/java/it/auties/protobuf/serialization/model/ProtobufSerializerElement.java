package it.auties.protobuf.serialization.model;

import javax.lang.model.element.ExecutableElement;

public record ProtobufSerializerElement(ExecutableElement element, String... arguments) implements ProtobufConverterElement {
}
