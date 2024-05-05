package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufMessage;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public class PreliminaryChecks {
    private final Messages messages;
    public PreliminaryChecks(Messages messages) {
        this.messages = messages;
    }

    public void checkDefaultValues(Set<? extends Element> elements) {
        for(var element : elements) {
            checkDefaultValue(element);
        }
    }

    private void checkDefaultValue(Element entry) {
        if(entry.getKind() != ElementKind.METHOD && (entry.getKind() != ElementKind.ENUM_CONSTANT || getEnclosingTypeElement(entry).getAnnotation(ProtobufEnum.class) == null)) {
            messages.printError("Invalid delegate: only methods, and enum constants in a ProtobufEnum, can be annotated with @ProtobufDefaultValue", entry);
            return;
        }

        if(entry.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufDefaultValue must have at least package-private visibility", entry);
            return;
        }

        if(!entry.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufDefaultValue must be static", entry);
        }
    }

    public void checkBuilders(Set<? extends Element> elements) {
        for(var element : elements) {
            checkBuilder(element);
        }
    }

    private void checkBuilder(Element element) {
        if(!(element instanceof ExecutableElement)) {
            messages.printError("Invalid delegate: only methods can be annotated with @ProtobufBuilder", element);
            return;
        }

        if(element.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufBuilder must have at least package-private visibility", element);
            return;
        }

        if(element.getKind() != ElementKind.CONSTRUCTOR && !element.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufBuilder must be a constructor or static", element);
        }
    }

    public void checkSerializers(Set<? extends Element> elements) {
        for(var element : elements) {
            checkSerializer(element);
        }
    }

    private void checkSerializer(Element element) {
        if(!(element instanceof ExecutableElement executableElement)) {
            messages.printError("Invalid delegate: only methods can be annotated with @ProtobufSerializer", element);
            return;
        }

        if(executableElement.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufSerializer must have at least package-private visibility", executableElement);
            return;
        }

        if(executableElement.getKind() == ElementKind.CONSTRUCTOR || executableElement.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufSerializer must be a non-static method", executableElement);
            return;
        }

        if(!executableElement.getParameters().isEmpty()) {
            messages.printError("Illegal method: a method annotated with @ProtobufSerializer mustn't take any parameters", executableElement);
        }
    }

    public void checkDeserializers(Set<? extends Element> elements) {
        for(var element : elements) {
            checkDeserializer(element);
        }
    }
    
    private void checkDeserializer(Element element) {
        if(!(element instanceof ExecutableElement executableElement)) {
            messages.printError("Invalid delegate: only methods can be annotated with @ProtobufDeserializer", element);
            return;
        }

        if(executableElement.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufDeserializer must have at least package-private visibility", executableElement);
            return;
        }

        if(executableElement.getKind() != ElementKind.CONSTRUCTOR && !executableElement.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer must be static or a constructor", executableElement);
            return;
        }

        if(executableElement.getEnclosingElement().asType().getAnnotation(ProtobufMessage.class) == null && executableElement.getParameters().size() > 1) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer must take exactly zero or one parameter", executableElement);
        }
    }
    
    @SafeVarargs
    public final void checkEnclosing(RoundEnvironment roundEnv, Class<? extends Annotation> annotation, String error, Class<? extends Annotation>... requiredAnnotations) {
        roundEnv.getElementsAnnotatedWith(annotation)
                .stream()
                .filter(property -> {
                    var enclosingTypeElement = getEnclosingTypeElement(property);
                    return Arrays.stream(requiredAnnotations)
                            .noneMatch(type -> enclosingTypeElement.getAnnotation(type) != null);
                })
                .forEach(property -> messages.printError(error, property));
    }
    
    private TypeElement getEnclosingTypeElement(Element element) {
        Objects.requireNonNull(element);
        if(element instanceof TypeElement typeElement) {
            return typeElement;
        }

        return getEnclosingTypeElement(element.getEnclosingElement());
    }
}
