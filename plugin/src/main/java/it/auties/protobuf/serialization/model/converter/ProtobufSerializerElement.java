package it.auties.protobuf.serialization.model.converter;

import it.auties.protobuf.serialization.model.property.ProtobufGroupPropertyElement;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Map;

public record ProtobufSerializerElement(
        ExecutableElement delegate,
        TypeMirror parameterType,
        TypeMirror returnType,
        Map<Integer, ProtobufGroupPropertyElement> groupProperties
) implements ProtobufConverterElement {

}
