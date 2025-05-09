package it.auties.protobuf.serialization.model.converter.attributed;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.serialization.model.converter.ProtobufConverterMethod;

import javax.lang.model.type.TypeMirror;

public record ProtobufAttributedConverterDeserializer(
        ProtobufConverterMethod delegate,
        TypeMirror parameterType,
        TypeMirror returnType,
        ProtobufDeserializer.BuilderBehaviour behaviour
) implements ProtobufAttributedConverterElement {

}
