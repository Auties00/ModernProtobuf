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
                && types.isAssignable(from, entry.getParameters().getFirst().asType())
                && types.isAssignable(to, entry.getReturnType());
        if(!result) {
            return Optional.empty();
        }

        return Optional.of(Objects.requireNonNullElse(deserializer.builderBehaviour(), BuilderBehaviour.DISCARD));
    }

    public boolean isSerializer(ExecutableElement entry, TypeMirror to, TypeMirror from) {
        // All serializers must be annotated with @ProtobufSerializer
        if(entry.getAnnotation(ProtobufSerializer.class) == null) {
            return false;
        }

        // Static serializers take a parameter, that is the object that they need to serialize
        // Class serializers take no parameters
        var isStatic = entry.getModifiers().contains(Modifier.STATIC);
        if(entry.getParameters().size() != (isStatic ? 1 : 0)) {
            return false;
        }

        // Check if the serializer is either not static or if it accepts the to type as a parameter
        if(isStatic && !types.isAssignable(to, entry.getParameters().getFirst().asType())) {
            return false;
        }

        // Check if the return type is accepted
        return types.isAssignable(from, entry.getReturnType());
    }
}
