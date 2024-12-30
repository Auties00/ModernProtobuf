package it.auties.protobuf.serialization.model.converter;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.serialization.model.property.ProtobufGroupPropertyElement;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Map;

public sealed interface ProtobufAttributedConverterElement extends ProtobufConverterElement {
    record Serializer(
            ExecutableElement delegate,
            TypeMirror parameterType,
            TypeMirror returnType,
            Map<Integer, ProtobufGroupPropertyElement> groupProperties
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
