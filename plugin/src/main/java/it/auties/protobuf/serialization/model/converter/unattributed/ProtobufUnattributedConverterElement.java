package it.auties.protobuf.serialization.model.converter.unattributed;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.converter.ProtobufConverterElement;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public record ProtobufUnattributedConverterElement(
        Element invoker,
        TypeMirror from,
        TypeMirror to,
        ProtobufType protobufType,
        List<TypeElement> mixins,
        ProtobufUnattributedConverterType type
) implements ProtobufConverterElement {

}
