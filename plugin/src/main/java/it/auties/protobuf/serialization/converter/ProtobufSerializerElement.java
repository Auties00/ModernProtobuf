package it.auties.protobuf.serialization.converter;

import javax.lang.model.element.ExecutableElement;

public record ProtobufSerializerElement(ExecutableElement element, boolean primitive, String... arguments) implements ProtobufConverterElement {

}
