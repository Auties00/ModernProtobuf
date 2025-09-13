package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufType;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import java.lang.annotation.Annotation;
import java.util.*;

public final class Checks {
    private final Types types;
    private final Messages messages;
    public Checks(Types types, Messages messages) {
        this.types = types;
        this.messages = messages;
    }

    public void runChecks(RoundEnvironment roundEnv) {
        checkMessages(roundEnv);
        checkGroups(roundEnv);
        checkMixins(roundEnv);
        checkMessageProperties(roundEnv);
        checkEnums(roundEnv);
        checkEnumProperties(roundEnv);
        checkAnyGetters(roundEnv);
        checkUnknownFields(roundEnv);
        checkSerializers(roundEnv);
        checkDeserializers(roundEnv);
        checkBuilders(roundEnv);
        checkDefaultValues(roundEnv);
        checkReservedRanges(roundEnv);
    }

    private void checkReservedRanges(RoundEnvironment roundEnv) {
        for(var element : roundEnv.getElementsAnnotatedWith(ProtobufMessage.class)) {
            var message = element.getAnnotation(ProtobufMessage.class);
            for(var range : message.reservedRanges()) {
                checkRange(element, range);
            }
        }
        for(var element : roundEnv.getElementsAnnotatedWith(ProtobufEnum.class)) {
            var enumeration = element.getAnnotation(ProtobufEnum.class);
            for(var range : enumeration.reservedRanges()) {
                checkRange(element, range);
            }
        }
        for(var element : roundEnv.getElementsAnnotatedWith(ProtobufGroup.class)) {
            var group = element.getAnnotation(ProtobufGroup.class);
            for(var range : group.reservedRanges()) {
                checkRange(element, range);
            }
        }
    }

    private void checkRange(Element element, ProtobufReservedRange range) {
        if(range.min() < 0) {
            messages.printError("Illegal annotation: min must be positive", element);
        }

        if(range.max() < 0) {
            messages.printError("Illegal annotation: max must be positive", element);
        }

        if(range.min() > range.max()) {
            messages.printError("Illegal annotation: max must be equal or bigger than min", element);
        }
    }

    private void checkUnknownFields(RoundEnvironment roundEnv) {
        var properties = roundEnv.getElementsAnnotatedWith(ProtobufUnknownFields.class);
        for (var property : properties) {
            checkUnknownField(property);
        }
    }

    private void checkUnknownField(Element property) {
        var enclosingElement = getEnclosingTypeElement(property);
        if(enclosingElement.getAnnotation(ProtobufMessage.class) == null) {
            messages.printError("Illegal enclosing class: a method or field annotated with @ProtobufUnknownFields should be enclosed by a class/record annotated with @ProtobufMessage", property);
            return;
        }

        var annotation = property.getAnnotation(ProtobufUnknownFields.class);
        var mixins = types.getMixins(annotation);
        checkMixins(property, mixins);
    }

    private void checkDefaultValues(RoundEnvironment roundEnv) {
        var defaultValues = roundEnv.getElementsAnnotatedWith(ProtobufDefaultValue.class);
        for(var defaultValue : defaultValues) {
            checkDefaultValue(defaultValue);
        }
    }

    private void checkDefaultValue(Element entry) {
        switch(entry.getKind()) {
            case METHOD -> {
                if(entry.getModifiers().contains(Modifier.PRIVATE)) {
                    messages.printError("Weak visibility: a method annotated with @ProtobufDefaultValue must have at least package-private visibility", entry);
                    return;
                }

                if(!entry.getModifiers().contains(Modifier.STATIC)) {
                    messages.printError("Illegal method: a method annotated with @ProtobufDefaultValue must be static", entry); // Enum constants are implicitly static
                    return;
                }

                var enclosingElement = getEnclosingTypeElement(entry);
                if(!types.isMixin(enclosingElement.asType()) && !types.isAssignable(((ExecutableElement) entry).getReturnType(), enclosingElement.asType())) {
                    messages.printError("Illegal method: a method annotated with @ProtobufDefaultValue must return a type assignable to its parent or be in a mixin", entry); // Enum constants are implicitly static
                }
            }

            case ENUM_CONSTANT -> {
                // All uses are fine
            }

            default -> messages.printError("Invalid delegate: only methods and enum constants can be annotated with @ProtobufDefaultValue", entry);
        }
    }

    private void checkEnumProperties(RoundEnvironment roundEnv) {
        checkEnclosing(
                roundEnv,
                ProtobufEnumIndex.class,
                "Illegal enclosing class: a field or parameter annotated with @ProtobufEnumIndex should be enclosed by an enum annotated with @ProtobufEnum",
                ProtobufEnum.class
        );
    }

    private void checkAnyGetters(RoundEnvironment roundEnv) {
        checkEnclosing(
                roundEnv,
                ProtobufAccessor.class,
                "Illegal enclosing class: a method annotated with @ProtobufGetter should be enclosed by a class or record annotated with @ProtobufMessage",
                ProtobufMessage.class
        );
    }

    private void checkMessageProperties(RoundEnvironment roundEnv) {
        var properties = roundEnv.getElementsAnnotatedWith(ProtobufProperty.class);
        for (var property : properties) {
            processMessageProperty(property);
        }
    }

    private void processMessageProperty(Element property) {
        var enclosingElement = getEnclosingTypeElement(property);
        if(enclosingElement.getAnnotation(ProtobufMessage.class) == null && enclosingElement.getAnnotation(ProtobufGroup.class) == null) {
            messages.printError("Illegal enclosing class: a field or method annotated with @ProtobufProperty should be enclosed by a class or record annotated with @ProtobufMessage or @ProtobufGroup", property);
            return;
        }

        var annotation = property.getAnnotation(ProtobufProperty.class);
        if(annotation.type() == ProtobufType.UNKNOWN) {
            messages.printError("Illegal protobuf type: a field or method annotated with @ProtobufProperty cannot have an UNKNOWN type", property);
            return;
        }

        var mixins = types.getMixins(annotation);
        checkMixins(property, mixins);
    }

    private void checkMixins(Element property, List<TypeElement> mixins) {
        for(var mixin : mixins) {
            if (!types.isMixin(mixin.asType())) {
                messages.printError("Illegal argument: %s is not a valid mixin".formatted(mixin.getSimpleName()), property);
            }
        }
    }

    private void checkEnums(RoundEnvironment roundEnv) {
        checkAnnotation(
                roundEnv,
                ProtobufEnum.class,
                "Illegal annotation: only enums can be annotated with @ProtobufEnum",
                ElementKind.ENUM
        );
    }

    private void checkMessages(RoundEnvironment roundEnv) {
        checkAnnotation(
                roundEnv,
                ProtobufMessage.class,
                "Illegal annotation: only classes and records can be annotated with @ProtobufMessage",
                ElementKind.CLASS,
                ElementKind.RECORD
        );
    }

    private void checkGroups(RoundEnvironment roundEnv) {
        checkAnnotation(
                roundEnv,
                ProtobufGroup.class,
                "Illegal annotation: only classes and records can be annotated with @ProtobufMessage",
                ElementKind.CLASS,
                ElementKind.RECORD
        );
    }

    private void checkMixins(RoundEnvironment roundEnv) {
        checkAnnotation(
                roundEnv,
                ProtobufMixin.class,
                "Illegal annotation: only classes and interfaces can be annotated with @ProtobufMixin",
                ElementKind.CLASS,
                ElementKind.INTERFACE
        );
    }

    private void checkBuilders(RoundEnvironment roundEnv) {
        var builders = roundEnv.getElementsAnnotatedWith(ProtobufBuilder.class);
        for(var builder : builders) {
            checkBuilder(builder);
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

    private void checkSerializers(RoundEnvironment roundEnv) {
        var serializers = roundEnv.getElementsAnnotatedWith(ProtobufSerializer.class);
        for(var serializer : serializers) {
            checkSerializer(serializer);
        }
    }

    private void checkSerializer(Element element) {
        if(!(element instanceof ExecutableElement executableElement)) {
            messages.printError("Invalid delegate: only methods can be annotated with @ProtobufSerializer", element);
            return;
        }

        if(executableElement.getKind() == ElementKind.CONSTRUCTOR) {
            messages.printError("Invalid delegate: constructors cannot be annotated with @ProtobufSerializer", element);
            return;
        }

        var enclosingType = executableElement.getEnclosingElement().asType();
        if(types.isObject(enclosingType)) {
            messages.printError("Illegal method: a method annotated with @ProtobufSerializer cannot be inside a ProtobufMessage, ProtobufEnum or ProtobufGroup", executableElement);
            return;
        }

        if(executableElement.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufSerializer must have at least package-private visibility", executableElement);
            return;
        }

        var inMixin = types.isMixin(enclosingType);
        if(executableElement.getModifiers().contains(Modifier.STATIC) != inMixin) {
            var message = inMixin ? "Illegal method: a method annotated with @ProtobufSerializer in a mixin must be static" : "Illegal method: a method annotated with @ProtobufSerializer must not be static";
            messages.printError(message, executableElement);
            return;
        }

        if(executableElement.getParameters().size() != (inMixin ? 1 : 0)) {
            var message = inMixin ? "Illegal method: a method annotated with @ProtobufSerializer in a mixin must take exactly one parameter" : "Illegal method: a method annotated with @ProtobufSerializer must take no parameters";
            messages.printError(message, executableElement);
            return;
        }

        var receiverType = executableElement.getReceiverType();
        if(receiverType != null && receiverType.getKind() != TypeKind.NONE) {
            messages.printError("Illegal method: a method annotated with @ProtobufSerializer cannot have a receiver type", executableElement);
            return;
        }

        if(executableElement.isVarArgs()) {
            messages.printError("Illegal method: a method annotated with @ProtobufSerializer cannot be varargs", executableElement);
        }
    }

    private void checkDeserializers(RoundEnvironment roundEnv) {
        var deserializers = roundEnv.getElementsAnnotatedWith(ProtobufDeserializer.class);
        for(var deserializer : deserializers) {
            checkDeserializer(deserializer);
        }
    }
    
    private void checkDeserializer(Element element) {
        if(!(element instanceof ExecutableElement executableElement)) {
            messages.printError("Invalid delegate: only methods can be annotated with @ProtobufDeserializer", element);
            return;
        }

        var enclosingType = executableElement.getEnclosingElement().asType();
        if(types.isObject(enclosingType)) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer cannot be inside a ProtobufMessage, ProtobufEnum or ProtobufGroup", executableElement);
            return;
        }

        if(executableElement.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufDeserializer must have at least package-private visibility", executableElement);
            return;
        }

        if(!executableElement.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer must be static", executableElement);
            return;
        }

        var inMixin = types.isMixin(enclosingType);
        if(executableElement.getKind() == ElementKind.CONSTRUCTOR && inMixin) {
            messages.printError( "Illegal method: a method annotated with @ProtobufDeserializer in a mixin cannot be a constructor", executableElement);
            return;
        }

        if(executableElement.getParameters().size() != 1) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer must take exactly one parameter", executableElement);
            return;
        }

        if(inMixin && !types.isAssignable(executableElement.getReturnType(), enclosingType)) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer must return a type assignable to its parent or be in a mixin", executableElement);
            return;
        }

        var receiverType = executableElement.getReceiverType();
        if(receiverType != null && receiverType.getKind() != TypeKind.NONE) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer cannot have a receiver type", executableElement);
            return;
        }

        if(executableElement.isVarArgs()) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer cannot be varargs", executableElement);
        }
    }
    
    private TypeElement getEnclosingTypeElement(Element element) {
        Objects.requireNonNull(element);
        if(element instanceof TypeElement typeElement) {
            return typeElement;
        }

        return getEnclosingTypeElement(element.getEnclosingElement());
    }

    @SafeVarargs
    private void checkEnclosing(RoundEnvironment roundEnv, Class<? extends Annotation> annotation, String error, Class<? extends Annotation>... requiredAnnotations) {
        roundEnv.getElementsAnnotatedWith(annotation)
                .stream()
                .filter(property -> {
                    var enclosingTypeElement = getEnclosingTypeElement(property);
                    return Arrays.stream(requiredAnnotations)
                            .noneMatch(type -> enclosingTypeElement.getAnnotation(type) != null);
                })
                .forEach(property -> messages.printError(error, property));
    }

    private void checkAnnotation(RoundEnvironment roundEnv, Class<? extends Annotation> protobufMessageClass, String error, ElementKind... elementKind) {
        var kinds = Set.of(elementKind);
        for(var element : roundEnv.getElementsAnnotatedWith(protobufMessageClass)) {
            if(element.getModifiers().contains(Modifier.PRIVATE)) {
                messages.printError("Weak visibility: a method annotated with @" + protobufMessageClass.getSimpleName() + " must have at least package-private visibility", element);
                return;
            }else if(!kinds.contains(element.getKind())) {
                messages.printError(error, element);
            }
        }
    }

    public boolean isValidRequiredProperty(Element variableElement) {
        if(variableElement.asType().getKind().isPrimitive()) {
            messages.printError("Required properties cannot be primitives", variableElement);
            return false;
        }

        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isValidPackedProperty(Element variableElement, ProtobufProperty propertyAnnotation) {
        if(!propertyAnnotation.packed() || types.isAssignable(variableElement.asType(), Collection.class)) {
            return true;
        }

        messages.printError("Only scalar properties can be packed", variableElement);
        return false;
    }
}
