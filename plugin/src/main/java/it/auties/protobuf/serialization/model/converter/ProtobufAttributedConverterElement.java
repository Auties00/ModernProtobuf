package it.auties.protobuf.serialization.model.converter;

import it.auties.protobuf.annotation.ProtobufDeserializer;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public sealed interface ProtobufAttributedConverterElement extends ProtobufConverterElement {
    record Serializer(
            ExecutableElement delegate,
            TypeMirror parameterType,
            TypeMirror returnType
    ) implements ProtobufAttributedConverterElement {

    }

    record Deserializer(
            ExecutableElement delegate,
            TypeMirror parameterType,
            TypeMirror returnType,
            ProtobufDeserializer.BuilderBehaviour behaviour
    ) implements ProtobufAttributedConverterElement {

    }
}
