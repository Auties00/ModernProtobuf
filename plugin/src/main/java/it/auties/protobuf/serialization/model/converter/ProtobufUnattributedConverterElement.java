package it.auties.protobuf.serialization.model.converter;

import it.auties.protobuf.model.ProtobufType;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public record ProtobufUnattributedConverterElement(
        javax.lang.model.element.Element invoker, TypeMirror from,
        TypeMirror to,
        ProtobufType protobufType,
        List<TypeElement> mixins,
        Type type
) implements ProtobufConverterElement {
    public enum Type {
        SERIALIZER,
        DESERIALIZER
    }
}
