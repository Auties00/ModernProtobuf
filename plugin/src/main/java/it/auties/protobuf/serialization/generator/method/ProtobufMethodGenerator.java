package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.model.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyVariables;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyVariables.ProtobufPropertyVariable;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public abstract class ProtobufMethodGenerator {
    protected final ProtobufObjectElement message;
    protected final List<Runnable> deferredOperations;
    protected ProtobufMethodGenerator(ProtobufObjectElement message) {
        this.message = message;
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

        deferredOperations.forEach(Runnable::run);
    }

    public abstract boolean shouldInstrument();

    protected abstract void doInstrumentation(ClassWriter classWriter, MethodWriter writer);

    protected abstract List<String> modifiers();

    protected abstract String returnType();

    protected abstract String name();

    protected abstract List<String> parametersTypes();

    protected abstract List<String> parametersNames();

    protected String getSpecFromObject(TypeMirror typeMirror) {
        if(!(typeMirror instanceof DeclaredType declaredType)) {
            return "";
        }

        var element = (TypeElement) declaredType.asElement();
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
    }

    protected String getAccessorCall(String object, ProtobufPropertyElement property) {
        return switch (property.accessor()) {
            case ExecutableElement executableElement -> "%s.%s()".formatted(object, executableElement.getSimpleName());
            case VariableElement variableElement -> "%s.%s".formatted(object, variableElement.getSimpleName());
            default -> throw new IllegalStateException("Unexpected value: " + property.accessor());
        };
    }

    protected ProtobufPropertyVariables getVariables(String name, String value, ProtobufPropertyType type) {
        var serializers = type.serializers();
        var variable = new ProtobufPropertyVariable(type.accessorType(), name, value, type.accessorType().getKind().isPrimitive());
        if (serializers.isEmpty()) {
            return new ProtobufPropertyVariables(false, List.of(variable));
        }

        var results = new ArrayList<ProtobufPropertyVariable>();
        results.add(variable);
        for (var index = 0; index < serializers.size(); index++) {
            var serializerElement = serializers.get(index);
            var lastInitializer = index == 0 ? name : name + (index - 1);
            var convertedInitializer = getConvertedInitializer(serializerElement, lastInitializer);
            var currentVariable = new ProtobufPropertyVariable(
                    serializerElement.returnType(),
                    name + index, convertedInitializer,
                    serializerElement.returnType().getKind().isPrimitive()
            );
            results.add(currentVariable);
        }

        return new ProtobufPropertyVariables(true, results);
    }

    private String getConvertedInitializer(ProtobufSerializerElement serializerElement, String lastInitializer) {
        if (serializerElement.delegate().getKind() == ElementKind.CONSTRUCTOR) {
            var converterWrapperClass = (TypeElement) serializerElement.delegate().getEnclosingElement();
            return "new %s(%s)".formatted(converterWrapperClass.getQualifiedName(), lastInitializer);
        }

        if (serializerElement.delegate().getModifiers().contains(Modifier.STATIC)) {
            var converterWrapperClass = (TypeElement) serializerElement.delegate().getEnclosingElement();
            return "%s.%s(%s)".formatted(converterWrapperClass.getQualifiedName(), serializerElement.delegate().getSimpleName(), lastInitializer);
        }

        return "%s.%s()".formatted(lastInitializer, serializerElement.delegate().getSimpleName());
    }
}