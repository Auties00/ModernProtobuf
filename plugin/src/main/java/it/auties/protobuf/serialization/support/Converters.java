package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour;
import it.auties.protobuf.annotation.ProtobufGetter;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufType;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Optional;

public class Converters {
    private static final String GETTER_PREFIX = "get";

    private final Types types;
    public Converters(Types types) {
        this.types = types;
    }

    public Optional<BuilderBehaviour> getDeserializer(ExecutableElement entry, TypeMirror to, TypeMirror from) {
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

    public Optional<ProtobufSerializer> getSerializer(ExecutableElement entry, TypeMirror to, TypeMirror from) {
        // All serializers must be annotated with @ProtobufSerializer
        var serializer = entry.getAnnotation(ProtobufSerializer.class);
        if(serializer == null) {
            return Optional.empty();
        }

        // Static serializers take a parameter, that is the object that they need to serialize
        // Class serializers take no parameters
        var isStatic = entry.getModifiers().contains(Modifier.STATIC);
        if(entry.getParameters().size() != (isStatic ? 1 : 0)) {
            return Optional.empty();
        }

        // Check if the serializer is either not static or if it accepts the to type as a parameter
        if(isStatic && !types.isAssignable(to, entry.getParameters().getFirst().asType())) {
            return Optional.empty();
        }

        // Check if the return type is accepted
        if(!types.isAssignable(from, entry.getReturnType())) {
            return Optional.empty();
        }

        return Optional.of(serializer);
    }

    public ProtobufProperty getProperty(ProtobufGetter getter) {
        return new ProtobufProperty() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ProtobufProperty.class;
            }

            @Override
            public int index() {
                return getter.index();
            }

            @Override
            public ProtobufType type() {
                return getter.type();
            }

            @Override
            public ProtobufType mapKeyType() {
                return ProtobufType.UNKNOWN;
            }

            @Override
            public ProtobufType mapValueType() {
                return ProtobufType.UNKNOWN;
            }

            @Override
            public Class<?>[] mixins() {
                return getter.mixins();
            }

            @Override
            public boolean required() {
                return false;
            }

            @Override
            public boolean ignored() {
                return false;
            }

            @Override
            public boolean packed() {
                return getter.packed();
            }
        };
    }

    public ProtobufProperty getProperty(ProtobufSerializer.GroupProperty property) {
        return new ProtobufProperty() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ProtobufProperty.class;
            }

            @Override
            public int index() {
                return property.index();
            }

            @Override
            public ProtobufType type() {
                return property.type();
            }

            @Override
            public ProtobufType mapKeyType() {
                return property.mapKeyType();
            }

            @Override
            public ProtobufType mapValueType() {
                return property.mapValueType();
            }

            @Override
            public Class<?>[] mixins() {
                return property.mixins();
            }

            @Override
            public boolean required() {
                return false;
            }

            @Override
            public boolean ignored() {
                return false;
            }

            @Override
            public boolean packed() {
                return property.packed();
            }
        };
    }

    public String getPropertyName(String string) {
        if(string.toLowerCase().startsWith(GETTER_PREFIX)) {
            return string.length() < GETTER_PREFIX.length() + 1 ? "" : string.substring(GETTER_PREFIX.length() + 1);
        }

        return string;
    }
}
