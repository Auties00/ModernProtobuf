package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufMessage;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
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

    public boolean isSubType(TypeMirror child, Class<?> parent) {
        return isSubType(child, getType(parent));
    }

    public boolean isSubType(TypeMirror child, TypeMirror parent) {
        if(child instanceof PrimitiveType primitiveType) {
            var boxed = processingEnv.getTypeUtils().boxedClass(primitiveType);
            child = boxed.asType();
        }

        if(parent instanceof PrimitiveType primitiveType) {
            var boxed = processingEnv.getTypeUtils().boxedClass(primitiveType);
            parent = boxed.asType();
        }

        return processingEnv.getTypeUtils().isSubtype(erase(child), erase(parent));
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
}
