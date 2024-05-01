package it.auties.protobuf.serialization.generator.clazz;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.object.ProtobufBuilderElement;
import it.auties.protobuf.serialization.object.ProtobufMessageElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyElement;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProtobufBuilderVisitor extends ProtobufClassVisitor {
    public ProtobufBuilderVisitor(Filer filer) {
        super(filer);
    }

    public void createClass(ProtobufMessageElement messageElement, ProtobufBuilderElement builderElement, PackageElement packageName) throws IOException {
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
                    writer.println("    private %s %s;".formatted(property.type().implementationType(), property.name()));
                    invocationArgs.add(property.name());
                }
            }
            writer.println();
            writer.println("    public %s() {".formatted(simpleGeneratedClassName));
            if(builderElement == null) {
                for (var property : messageElement.properties()) {
                    writer.println("        %s = %s;".formatted(property.name(), property.type().defaultValue()));
                }
            }

            writer.println("    }");
            writer.println();
            if(builderElement != null) {
                for(var parameter : builderElement.parameters()) {
                    var fieldName = parameter.getSimpleName().toString();
                    writeBuilderSetter(writer, fieldName, fieldName, parameter.asType(), simpleGeneratedClassName);
                }
            }else {
                for(var property : messageElement.properties()) {
                    var done = false;
                    for(var override : getBuilderOverloads(property)) {
                        var enclosingElement = (TypeElement) override.element().getEnclosingElement();
                        var fieldValue = override.element().getModifiers().contains(Modifier.STATIC)
                                ? "%s.%s(%s)".formatted(enclosingElement.getQualifiedName(), override.element().getSimpleName(), property.name())
                                : "new %s(%s)".formatted(enclosingElement.getQualifiedName(), property.name());
                        writeBuilderSetter(
                                writer,
                                property.name(),
                                fieldValue,
                                override.parameterType(),
                                simpleGeneratedClassName
                        );
                        done |= override.behaviour() == ProtobufDeserializer.BuilderBehaviour.OVERRIDE;
                    }

                    if(!done) {
                        writeBuilderSetter(
                                writer,
                                property.name(),
                                property.name(),
                                property.type().implementationType(),
                                simpleGeneratedClassName
                        );
                    }
                }
            }
            var resultQualifiedName = messageElement.element().getQualifiedName();
            var invocationArgsJoined = String.join(", ", invocationArgs);
            writer.println("    public %s build() {".formatted(resultQualifiedName));
            var invocation = builderElement == null ? "new %s(%s)".formatted(resultQualifiedName, invocationArgsJoined) : "%s.%s(%s)".formatted(resultQualifiedName, builderElement.delegate().getSimpleName(), invocationArgsJoined);
            writer.println("        return %s;".formatted(invocation));
            writer.println("    }");
            writer.println("}");
        }
    }

    private List<ProtobufDeserializerElement> getBuilderOverloads(ProtobufPropertyElement element) {
        var results = new ArrayList<ProtobufDeserializerElement>();
        for(var converter : element.type().deserializers()) {
            if(converter.behaviour() == ProtobufDeserializer.BuilderBehaviour.OVERRIDE) {
                return List.of(converter);
            }

            if(converter.behaviour() != ProtobufDeserializer.BuilderBehaviour.DISCARD) {
                results.add(converter);
            }
        }

        return Collections.unmodifiableList(results);
    }

    private void writeBuilderSetter(PrintWriter writer, String fieldName, String fieldValue, TypeMirror fieldType, String className) {
        writer.println("    public %s %s(%s %s) {".formatted(className, fieldName, fieldType, fieldName));
        writer.println("        this.%s = %s;".formatted(fieldName, fieldValue));
        writer.println("        return this;");
        writer.println("    }");
        writer.println();
    }
}
