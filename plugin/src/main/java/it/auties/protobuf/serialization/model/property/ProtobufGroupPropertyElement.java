package it.auties.protobuf.serialization.model.property;

import javax.lang.model.element.TypeElement;
import java.util.List;

public record ProtobufGroupPropertyElement(
        int index,
        ProtobufPropertyType type,
        boolean packed,
        List<TypeElement> mixins) {

}
