package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.ProtobufConverter;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

public class Converters {
    private final Types types;
    public Converters(Types types) {
        this.types = types;
    }

    public boolean isDeserializer(ExecutableElement entry, TypeMirror from) {
        return entry.getAnnotation(ProtobufConverter.class) != null
                && entry.getModifiers().contains(Modifier.STATIC)
                && entry.getParameters().size() == 1
                && types.isSubType(from, entry.getParameters().getFirst().asType());
    }

    public boolean isSerializer(ExecutableElement entry, TypeMirror to, TypeMirror from) {
        var isStatic = entry.getModifiers().contains(Modifier.STATIC);
        return entry.getAnnotation(ProtobufConverter.class) != null
                && entry.getParameters().size() == (isStatic ? 1 : 0)
                && (!isStatic || types.isSubType(to, entry.getParameters().getFirst().asType()))
                && types.isSubType(from, entry.getReturnType());
    }
}
