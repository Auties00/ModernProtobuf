package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.*;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Base class for all method generators that create individual methods in Spec classes using JavaPoet
// Provides method signature construction, Spec class name resolution, deferred operations, and type utilities
public abstract class ProtobufMethodGenerator {
    private static final ConcurrentMap<String, String> SPECS_CACHE = new ConcurrentHashMap<>();

    // Converts a protobuf type to its generated Spec class name
    //
    // Examples:
    //   com.example.Message -> com.example.MessageSpec
    //   com.example.Outer.Inner -> com.example.OuterInnerSpec
    //
    // Flow:
    //   1. Walk up the enclosing element chain to build the nested class path
    //   2. Find the package name
    //   3. Concatenate package + nested names + "Spec" suffix
    //   4. Cache result for performance
    public static String getSpecFromObject(TypeMirror typeMirror) {
        if(!(typeMirror instanceof DeclaredType declaredType)
                || !(declaredType.asElement() instanceof TypeElement element)) {
            return "";
        }

        return SPECS_CACHE.computeIfAbsent(element.getQualifiedName().toString(), _ -> {
            // Walk up enclosing elements to build class hierarchy
            var parent = element.getEnclosingElement();
            String packageName = null;
            var name = new StringBuilder();
            while (parent != null) {
                if(parent instanceof TypeElement typeElement) {
                    // Nested class: prepend parent name
                    name.append(typeElement.getSimpleName());
                }else if(parent instanceof PackageElement packageElement) {
                    // Found package: stop walking
                    packageName = packageElement.getQualifiedName().toString();
                    break;
                }

                parent = parent.getEnclosingElement();
            }

            // Append the type's own name
            name.append(declaredType.asElement().getSimpleName());

            // Build final Spec class name: package.OuterInnerSpec
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

    public void generate(TypeSpec.Builder classBuilder) {
        if (!shouldInstrument()) {
            return;
        }

        var parametersTypes = parametersTypes();
        var parametersNames = parametersNames();
        if(parametersTypes.size() != parametersNames.size()) {
            throw new IllegalArgumentException("Parameters mismatch");
        }

        var methodBuilder = MethodSpec.methodBuilder(name());
        methodBuilder.addModifiers(modifiers().toArray(new Modifier[0]));
        methodBuilder.returns(returnType());

        for (int i = 0; i < parametersTypes.size(); i++) {
            var paramType = parametersTypes.get(i);
            var paramName = parametersNames.get(i);
            methodBuilder.addParameter(ParameterSpec.builder(paramType, paramName).build());
        }

        doInstrumentation(classBuilder, methodBuilder);
        classBuilder.addMethod(methodBuilder.build());

        while (!deferredOperations.isEmpty()) {
            var round = new ArrayList<>(deferredOperations);
            deferredOperations.clear();
            for(var runnable : round) {
                runnable.run();
            }
        }
    }

    public abstract boolean shouldInstrument();

    protected abstract void doInstrumentation(TypeSpec.Builder classBuilder, MethodSpec.Builder methodBuilder);

    protected abstract List<Modifier> modifiers();

    protected abstract TypeName returnType();

    protected abstract String name();

    protected abstract List<TypeName> parametersTypes();

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