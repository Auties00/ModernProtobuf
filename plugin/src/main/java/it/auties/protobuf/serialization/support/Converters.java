package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour;
import it.auties.protobuf.annotation.ProtobufSerializer;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;
import java.util.Optional;

public class Converters {
    private final Types types;
    public Converters(Types types) {
        this.types = types;
    }

    public Optional<BuilderBehaviour> getDeserializerBuilderBehaviour(ExecutableElement entry, TypeMirror to, TypeMirror from) {
        var deserializer = entry.getAnnotation(ProtobufDeserializer.class);
        if(deserializer == null) {
            return Optional.empty();
        }


        var result = entry.getModifiers().contains(Modifier.STATIC)
                && entry.getParameters().size() == 1
                && types.isSubType(from, entry.getParameters().getFirst().asType())
                && types.isSubType(to, entry.getReturnType());
        if(!result) {
            return Optional.empty();
        }

        return Optional.of(Objects.requireNonNullElse(deserializer.builderBehaviour(), BuilderBehaviour.DISCARD));
    }

    public boolean isSerializer(ExecutableElement entry, TypeMirror to, TypeMirror from) {
        var isStatic = entry.getModifiers().contains(Modifier.STATIC);
        return entry.getAnnotation(ProtobufSerializer.class) != null
                && entry.getParameters().size() == (isStatic ? 1 : 0)
                && (!isStatic || types.isSubType(to, entry.getParameters().getFirst().asType()))
                && types.isSubType(from, entry.getReturnType());
    }
}
