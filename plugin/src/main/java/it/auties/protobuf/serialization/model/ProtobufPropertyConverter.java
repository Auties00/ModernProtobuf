package it.auties.protobuf.serialization.model;

import javax.lang.model.element.ExecutableElement;

public record ProtobufPropertyConverter(ExecutableElement serializer, ExecutableElement deserializer) {

}
