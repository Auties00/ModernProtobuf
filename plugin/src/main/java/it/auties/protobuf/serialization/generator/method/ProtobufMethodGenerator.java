package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.object.ProtobufMessageElement;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class ProtobufMethodGenerator {
    protected final ProtobufMessageElement message;
    protected final PrintWriter writer;

    protected ProtobufMethodGenerator(ProtobufMessageElement message, PrintWriter writer) {
        this.message = message;
        this.writer = writer;
    }

    public void instrument() {
        if (!shouldInstrument()) {
            return;
        }

        writer.print("    ");
        writer.print(String.join(" ", modifiers()));
        writer.print(" ");
        writer.print(returnType());
        writer.print(" ");
        writer.print(name());
        writer.print("(");
        var parametersTypes = parametersTypes();
        var parametersNames = parametersNames();
        if(parametersTypes.size() != parametersNames.size()) {
            throw new IllegalArgumentException("Parameters mismatch");
        }

        var parametersTypesIterator = parametersTypes.iterator();
        var parametersNamesIterator = parametersNames.iterator();
        var parameters = new ArrayList<String>();
        while (parametersTypesIterator.hasNext()) {
            parameters.add("%s %s".formatted(parametersTypesIterator.next(), parametersNamesIterator.next()));
        }

        writer.print(String.join(", ", parameters));
        writer.print(") {\n");
        doInstrumentation();
        writer.println("    }\n");
    }

    public abstract boolean shouldInstrument();

    protected abstract void doInstrumentation();

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