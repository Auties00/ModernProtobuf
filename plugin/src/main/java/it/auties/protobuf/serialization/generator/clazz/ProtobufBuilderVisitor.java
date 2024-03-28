package it.auties.protobuf.serialization.generator.clazz;

import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.object.ProtobufBuilderElement;
import it.auties.protobuf.serialization.object.ProtobufMessageElement;

import javax.annotation.processing.Filer;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;

public class ProtobufBuilderVisitor extends ProtobufClassVisitor {
    private final Map<String, ProtobufDeserializerElement> atomicDeserializers;
    public ProtobufBuilderVisitor(Filer filer, Map<String, ProtobufDeserializerElement> atomicDeserializers) {
        super(filer);
        this.atomicDeserializers = atomicDeserializers;
    }

    private void createBuilderClass(ProtobufMessageElement messageElement, ProtobufBuilderElement builderElement, PackageElement packageName) throws IOException {
        var simpleGeneratedClassName = builderElement != null ? messageElement.getGeneratedClassNameByName(builderElement.name()) : messageElement.getGeneratedClassNameBySuffix("Builder");
        var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;
        var sourceFile = filer.createSourceFile(qualifiedGeneratedClassName);
        try (var writer = new PrintWriter(sourceFile.openWriter())) {
            if(packageName != null) {
                writer.println("package %s;\n".formatted(packageName.getQualifiedName()));
            }

            writer.println("public class %s {".formatted(simpleGeneratedClassName));
            var invocationArgs = new ArrayList<String>();
            if(builderElement != null) {
                for(var parameter : builderElement.parameters()) {
                    writer.println("    private %s %s;".formatted(parameter.asType(), parameter.getSimpleName()));
                    invocationArgs.add(parameter.getSimpleName().toString());
                }
            }else {
                for (var property : messageElement.properties()) {
                    writer.println("    private %s %s;".formatted(property.type().descriptorElementType(), property.name()));
                    invocationArgs.add(property.name());
                }
            }
            writer.println();
            writer.println("    public %s() {".formatted(simpleGeneratedClassName));
            if(builderElement == null) {
                for (var property : messageElement.properties()) {
                    writer.println("        %s = %s;".formatted(property.name(), property.defaultValue()));
                }
            }

            writer.println("    }");
            writer.println();
            if(builderElement != null) {
                for(var parameter : builderElement.parameters()) {
                    writeBuilderSetter(writer, parameter.getSimpleName().toString(), parameter.asType(), simpleGeneratedClassName);
                }
            }else {
                for(var property : messageElement.properties()) {
                    writeBuilderSetter(writer, property.name(), property.type().descriptorElementType(), simpleGeneratedClassName);
                }
            }
            writer.println();
            var resultQualifiedName = messageElement.element().getQualifiedName();
            var invocationArgsJoined = String.join(", ", invocationArgs);
            writer.println("    public %s build() {".formatted(resultQualifiedName));
            var invocation = builderElement == null ? "new %s(%s)".formatted(resultQualifiedName, invocationArgsJoined) : "%s.%s(%s)".formatted(resultQualifiedName, builderElement.delegate().getSimpleName(), invocationArgsJoined);
            writer.println("        return %s;".formatted(invocation));
            writer.println("    }");
            writer.println("}");
        }
    }

    private void writeBuilderSetter(PrintWriter writer, String fieldName, TypeMirror fieldType, String className) {
        writer.println("    public %s %s(%s %s) {".formatted(className, fieldName, fieldType, fieldName));
        writer.println("        this.%s = %s;".formatted(fieldName, fieldName));
        writer.println("        return this;");
        writer.println("    }");
        if (!(fieldType instanceof DeclaredType declaredType)) {
            return;
        }

        if(!(declaredType.asElement() instanceof TypeElement typeElement)) {
            return;
        }

        var optionalConverter = optionalDeserializers.get(typeElement.getQualifiedName().toString());
        if(optionalConverter != null) {
            var optionalValueType = getOptionalValueType(declaredType);
            if(optionalValueType.isEmpty()) {
                return;
            }

            writer.println("    public %s %s(%s %s) {".formatted(className, fieldName, optionalValueType.get(), fieldName));
            var converterWrapperClass = (TypeElement) optionalConverter.element().getEnclosingElement();
            writer.println("        this.%s = %s.%s(%s);".formatted(fieldName, converterWrapperClass.getQualifiedName(), optionalConverter.element().getSimpleName(), fieldName));
            writer.println("        return this;");
            writer.println("    }");
            return;
        }

        var atomicConverter = atomicDeserializers.get(typeElement.getQualifiedName().toString());
        if(atomicConverter != null) {
            var atomicValueType = getAtomicValueType(declaredType);
            if(atomicValueType.isEmpty()) {
                return;
            }

            writer.println("    public %s %s(%s %s) {".formatted(className, fieldName, atomicValueType.get(), fieldName));
            var converterWrapperClass = (TypeElement) atomicConverter.element().getEnclosingElement();
            writer.println("        this.%s = new %s(%s);".formatted(fieldName, converterWrapperClass.getQualifiedName(), fieldName));
            writer.println("        return this;");
            writer.println("    }");
        }
    }
}
