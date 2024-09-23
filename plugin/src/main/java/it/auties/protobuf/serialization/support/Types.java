package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public class Types {
    private final ProcessingEnvironment processingEnv;
    public Types(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    // Convert a Java type into an AST type mirror
    public TypeMirror getType(Class<?> type, Class<?>... params) {
        if(type.isPrimitive()) {
            var kind = TypeKind.valueOf(type.getName().toUpperCase(Locale.ROOT));
            return processingEnv.getTypeUtils().getPrimitiveType(kind);
        }

        if(type.isArray()) {
            return processingEnv.getTypeUtils().getArrayType(getType(type.getComponentType()));
        }

        var result = processingEnv.getElementUtils().getTypeElement(type.getName());
        if(params.length == 0) {
            return erase(result.asType());
        }else {
            var typeArgs = Arrays.stream(params)
                    .map(this::getType)
                    .toArray(TypeMirror[]::new);
            return processingEnv.getTypeUtils().getDeclaredType(result, typeArgs);
        }
    }

    public boolean isGroup(TypeMirror mirror) {
        return erase(mirror) instanceof DeclaredType declaredType
                && declaredType.asElement().getAnnotation(ProtobufGroup.class) != null;
    }

    public boolean isMessage(TypeMirror mirror) {
        return erase(mirror) instanceof DeclaredType declaredType
                && declaredType.asElement().getAnnotation(ProtobufMessage.class) != null;
    }

    public boolean isEnum(TypeMirror mirror) {
        return erase(mirror) instanceof DeclaredType declaredType
                && declaredType.asElement().getKind() == ElementKind.ENUM
                && declaredType.asElement().getAnnotation(ProtobufEnum.class) != null;
    }

    public boolean isSameType(TypeMirror firstType, Class<?> secondType) {
        return isSameType(firstType, getType(secondType));
    }

    public boolean isSameType(TypeMirror firstType, TypeMirror secondType) {
        return processingEnv.getTypeUtils().isSameType(erase(firstType), erase(secondType));
    }

    public TypeMirror erase(TypeMirror typeMirror) {
        var result = processingEnv.getTypeUtils().erasure(typeMirror);
        return result == null ? typeMirror : result;
    }

    public boolean isAssignable(TypeMirror rhs, Class<?> lhs) {
        return isAssignable(rhs, getType(lhs));
    }

    public boolean isAssignable(TypeMirror rhs, TypeMirror lhs) {
        if(rhs instanceof PrimitiveType primitiveType) {
            var boxed = processingEnv.getTypeUtils().boxedClass(primitiveType);
            rhs = boxed.asType();
        }

        if(lhs instanceof PrimitiveType primitiveType) {
            var boxed = processingEnv.getTypeUtils().boxedClass(primitiveType);
            lhs = boxed.asType();
        }

        return processingEnv.getTypeUtils().isAssignable(erase(rhs), erase(lhs));
    }

    public Optional<TypeElement> getTypeWithDefaultConstructor(TypeMirror collectionType) {
        if(erase(collectionType) instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement
                && !typeElement.getModifiers().contains(Modifier.ABSTRACT)
                && hasNoArgsConstructor(typeElement)) {
            return Optional.of(typeElement);
        }

        return Optional.empty();
    }

    private boolean hasNoArgsConstructor(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
                .stream()
                .anyMatch(entry -> entry.getKind() == ElementKind.CONSTRUCTOR && ((ExecutableElement) entry).getParameters().isEmpty());
    }

    public List<TypeElement> getMixins(ProtobufProperty property) {
        return getMirroredTypes(property::mixins);
    }

    public List<TypeElement> getMixins(ProtobufUnknownFields property) {
        return getMirroredTypes(property::mixins);
    }

    public List<TypeElement> getMixins(ProtobufGetter property) {
        return getMirroredTypes(property::mixins);
    }

    public TypeElement getMirroredType(Supplier<Class<?>> supplier) {
        try {
            return processingEnv.getElementUtils().getTypeElement(supplier.get().getName());
        }catch (MirroredTypeException exception) {
            return (TypeElement) ((DeclaredType) exception.getTypeMirror()).asElement();
        }
    }

    public List<TypeElement> getMirroredTypes(Supplier<Class<?>[]> supplier) {
        try {
            return Arrays.stream(supplier.get())
                    .map(mixin -> processingEnv.getElementUtils().getTypeElement(mixin.getName()))
                    .filter(entry -> entry instanceof DeclaredType)
                    .map(entry -> (TypeElement) ((DeclaredType) entry).asElement())
                    .toList();
        }catch (MirroredTypesException exception) {
            return exception.getTypeMirrors()
                    .stream()
                    .filter(entry -> entry instanceof DeclaredType)
                    .map(entry -> (TypeElement) ((DeclaredType) entry).asElement())
                    .toList();
        }
    }
}
