package it.auties.protobuf.serialization.model.converter.attributed;

import it.auties.protobuf.serialization.model.converter.ProtobufConverterMethod;

import javax.lang.model.type.TypeMirror;

public record ProtobufAttributedConverterSerializer(
        ProtobufConverterMethod delegate,
        TypeMirror parameterType,
        TypeMirror returnType
) implements ProtobufAttributedConverterElement {

}
