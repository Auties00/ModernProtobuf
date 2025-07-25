package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.writer.ClassWriter;
import it.auties.protobuf.serialization.writer.MethodWriter;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

public abstract class ProtobufMethodGenerator {
    private static final ConcurrentMap<String, String> SPECS_CACHE = new ConcurrentHashMap<>();

    public static String getSpecFromObject(TypeMirror typeMirror) {
        if(!(typeMirror instanceof DeclaredType declaredType)
                || !(declaredType.asElement() instanceof TypeElement element)) {
            return "";
        }

        return SPECS_CACHE.computeIfAbsent(element.getQualifiedName().toString(), owner -> {
            var parent = element.getEnclosingElement();
            String packageName = null;
            var name = new StringBuilder();
            while (parent != null) {
                if(parent instanceof TypeElement typeElement) {
                    name.append(typeElement.getSimpleName());
                }else if(parent instanceof PackageElement packageElement) {
                    packageName = packageElement.getQualifiedName().toString();
                    break;
                }

                parent = parent.getEnclosingElement();
            }
            name.append(declaredType.asElement().getSimpleName());
            var result = new StringBuilder();
            if(packageName != null) {
                result.append(packageName);
                result.append(".");
            }
            result.append(name);
            result.append("Spec");
            return result.toString();
        });
    }


    protected final ProtobufObjectElement objectElement;
    protected final List<Runnable> deferredOperations;
    protected ProtobufMethodGenerator(ProtobufObjectElement objectElement) {
        this.objectElement = objectElement;
        this.deferredOperations = new ArrayList<>();
    }

    public void generate(ClassWriter writer) {
        if (!shouldInstrument()) {
            return;
        }

        var parametersTypes = parametersTypes();
        var parametersNames = parametersNames();
        if(parametersTypes.size() != parametersNames.size()) {
            throw new IllegalArgumentException("Parameters mismatch");
        }

        var parameters = IntStream.range(0, parametersTypes.size())
                .mapToObj(index -> parametersTypes.get(index) + " " + parametersNames.get(index))
                .toArray(String[]::new);
        try(var methodWriter = writer.printMethodDeclaration(modifiers(), returnType(), name(), parameters)) {
            doInstrumentation(writer, methodWriter);
        }

        while (!deferredOperations.isEmpty()) {
            var round = new ArrayList<>(deferredOperations);
            deferredOperations.clear();
            for(var runnable : round) {
                runnable.run();
            }
        }
    }

    public abstract boolean shouldInstrument();

    protected abstract void doInstrumentation(ClassWriter classWriter, MethodWriter writer);

    protected abstract List<String> modifiers();

    protected abstract String returnType();

    protected abstract String name();

    protected abstract List<String> parametersTypes();

    protected abstract List<String> parametersNames();

    protected String getAccessorCall(String object, Element accessor) {
        return switch (accessor) {
            case ExecutableElement executableElement -> "%s.%s()".formatted(object, executableElement.getSimpleName());
            case VariableElement variableElement -> "%s.%s".formatted(object, variableElement.getSimpleName());
            default -> throw new IllegalStateException("Unexpected value: " + accessor);
        };
    }

    protected String getQualifiedName(TypeMirror type) {
        if(!(type instanceof DeclaredType declaredType)) {
            return type.toString();
        }

        if((!(declaredType.asElement() instanceof TypeElement typeElement))) {
            return declaredType.toString();
        }

        return typeElement.getQualifiedName().toString();
    }

    protected String getSimpleName(TypeMirror type) {
        var parts = getQualifiedName(type).split("\\.");
        return parts[parts.length - 1].replaceAll("\\$", ".");
    }

    protected boolean isMessage(TypeMirror type) {
        return type instanceof DeclaredType declaredType
                && declaredType.asElement().getAnnotation(ProtobufMessage.class) != null;
    }

    protected boolean isGroup(TypeMirror type) {
        return type instanceof DeclaredType declaredType
                && declaredType.asElement().getAnnotation(ProtobufGroup.class) != null;
    }

    protected boolean isEnum(TypeMirror deserializedType) {
        return deserializedType instanceof DeclaredType declaredType
                && declaredType.asElement().getAnnotation(ProtobufEnum.class) != null;
    }
}