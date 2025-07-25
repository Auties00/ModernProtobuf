package it.auties.protobuf.serialization.model;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.*;

public sealed interface ProtobufConverterMethod {
    Optional<ExecutableElement> element();
    String ownerName();
    Set<Modifier> modifiers();
    TypeMirror returnType();
    String name();
    boolean parametrized();
    List<TypeMirror> parameters();
    <T extends Annotation> T getAnnotation(Class<T> annotation);

    static ProtobufConverterMethod of(ExecutableElement element, boolean parametrized) {
        return new Element(element, parametrized);
    }

    static ProtobufConverterMethod of(String owner, Set<Modifier> modifiers, TypeMirror returnType, String name, TypeMirror... parameters) {
        return new Synthetic(owner, modifiers, returnType, name, parameters);
    }

    final class Element implements ProtobufConverterMethod {
        private final ExecutableElement element;
        private final boolean parametrized;

        private Element(ExecutableElement element, boolean parametrized) {
            this.element = element;
            this.parametrized = parametrized;
        }

        @Override
        public Optional<ExecutableElement> element() {
            return Optional.of(element);
        }

        @Override
        public boolean parametrized() {
            return parametrized;
        }

        @Override
        public String ownerName() {
            var typeElement = (TypeElement) element.getEnclosingElement();
            return typeElement.getQualifiedName().toString();
        }

        @Override
        public TypeMirror returnType() {
            return element.getReturnType();
        }

        @Override
        public String name() {
            return element.getSimpleName().toString();
        }

        @Override
        public List<TypeMirror> parameters() {
            return element.getParameters()
                    .stream()
                    .map(VariableElement::asType)
                    .toList();
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotation) {
            return element.getAnnotation(annotation);
        }

        @Override
        public Set<Modifier> modifiers() {
            return element.getModifiers();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Element that
                    && Objects.equals(element, that.element);
        }

        @Override
        public int hashCode() {
            return element.hashCode();
        }

        @Override
        public String toString() {
            return ownerName() + "#" + name();
        }
    }

    final class Synthetic implements ProtobufConverterMethod {
        private final String ownerName;
        private final Set<Modifier> modifiers;
        private final String name;
        private final TypeMirror returnType;
        private final List<TypeMirror> parameters;

        private Synthetic(String ownerName, Set<Modifier> modifiers, TypeMirror returnType, String name, TypeMirror... parameters) {
            this.ownerName = ownerName;
            this.modifiers = modifiers;
            this.name = name;
            this.returnType = returnType;
            this.parameters = Arrays.asList(parameters);
        }

        @Override
        public Optional<ExecutableElement> element() {
            return Optional.empty();
        }

        @Override
        public boolean parametrized() {
            return false;
        }

        @Override
        public String ownerName() {
            return ownerName;
        }

        @Override
        public TypeMirror returnType() {
            return returnType;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<TypeMirror> parameters() {
            return parameters;
        }

        @Override
        public Set<Modifier> modifiers() {
            return modifiers;
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotation) {
            if(annotation.getName().equals(ProtobufDeserializer.class.getName())) {
                return (T) new ProtobufDeserializer() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return ProtobufDeserializer.class;
                    }

                    @Override
                    public BuilderBehaviour builderBehaviour() {
                        return BuilderBehaviour.DISCARD;
                    }

                    @Override
                    public String warning() {
                        return "";
                    }
                };
            }else if(annotation.getName().equals(ProtobufSerializer.class.getName())) {
                return (T) new ProtobufSerializer() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return ProtobufSerializer.class;
                    }

                    @Override
                    public String warning() {
                        return "";
                    }
                };
            }else {
                return null;
            }
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Synthetic that
                    && Objects.equals(ownerName, that.ownerName)
                    && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerName, name);
        }

        @Override
        public String toString() {
            return ownerName() + "#" + name();
        }
    }
}
