package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.object.ProtobufMessageElement;
import it.auties.protobuf.serialization.support.CompilationUnitWriter.NestedClassWriter;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.stream.IntStream;

public abstract class ProtobufMethodGenerator {
    protected final ProtobufMessageElement message;
    private final NestedClassWriter writer;
    protected ProtobufMethodGenerator(ProtobufMessageElement message, NestedClassWriter writer) {
        this.message = message;
        this.writer = writer;
    }

    public void generate() {
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
            doInstrumentation(methodWriter);
        }
    }

    public abstract boolean shouldInstrument();

    protected abstract void doInstrumentation(NestedClassWriter writer);

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
}