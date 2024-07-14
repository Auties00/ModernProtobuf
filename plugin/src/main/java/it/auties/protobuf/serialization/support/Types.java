package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufMessage;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Locale;
import java.util.Optional;

public class Types {
    private final ProcessingEnvironment processingEnv;
    public Types(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    // Convert a Java type into an AST type mirror
    public TypeMirror getType(Class<?> type) {
        if(type.isPrimitive()) {
            var kind = TypeKind.valueOf(type.getName().toUpperCase(Locale.ROOT));
            return processingEnv.getTypeUtils().getPrimitiveType(kind);
        }

        if(type.isArray()) {
            return processingEnv.getTypeUtils().getArrayType(getType(type.getComponentType()));
        }

        var result = processingEnv.getElementUtils().getTypeElement(type.getName());
        return erase(result.asType());
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

    public TypeMirror newType(Class<?> type, TypeMirror... typeArguments) {
        var astType = getType(type);
        if(!(astType instanceof DeclaredType declaredType)) {
            return astType;
        }

        var element = (TypeElement) declaredType.asElement();
        return processingEnv.getTypeUtils().getDeclaredType(element, typeArguments);
    }

    public Optional<String> getName(TypeMirror type) {
        if(!(type instanceof DeclaredType declaredType)) {
            return Optional.empty();
        }

        if(!(declaredType.asElement() instanceof TypeElement typeElement)) {
            return Optional.empty();
        }

        return Optional.ofNullable(typeElement.getQualifiedName().toString());
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
}
