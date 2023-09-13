package it.auties.protobuf.serialization.model;

import javax.lang.model.element.ExecutableElement;

public record ProtobufConverterElement(ExecutableElement serializer, ExecutableElement deserializer, String... serializerArguments) {

}
