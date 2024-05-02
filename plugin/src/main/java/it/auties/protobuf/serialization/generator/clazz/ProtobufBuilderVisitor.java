package it.auties.protobuf.serialization.generator.clazz;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.object.ProtobufBuilderElement;
import it.auties.protobuf.serialization.object.ProtobufMessageElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.support.CompilationUnitWriter;
import it.auties.protobuf.serialization.support.CompilationUnitWriter.NestedClassWriter;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProtobufBuilderVisitor extends ProtobufClassVisitor {
    public ProtobufBuilderVisitor(Filer filer) {
        super(filer);
    }

    public void createClass(ProtobufMessageElement messageElement, ProtobufBuilderElement builderElement, PackageElement packageName) throws IOException {
        // Names
        var simpleGeneratedClassName = builderElement != null ? messageElement.getGeneratedClassNameByName(builderElement.name()) : messageElement.getGeneratedClassNameBySuffix("Builder");
        var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;
        var sourceFile = filer.createSourceFile(qualifiedGeneratedClassName);

        // Declare a new compilation unit
        try (var compilationUnitWriter = new CompilationUnitWriter(sourceFile.openWriter())) {
            // If a package is available, write it in the compilation unit
            if(packageName != null) {
                compilationUnitWriter.printPackageDeclaration(packageName.getQualifiedName().toString());
            }

            // Declare the builder class
            try(var builderClassWriter = compilationUnitWriter.printClassDeclaration(simpleGeneratedClassName)) {
                // Write the fields of the builder and collect them
                var invocationArgs = new ArrayList<String>();
                if(builderElement != null) {
                    for(var parameter : builderElement.parameters()) {
                        builderClassWriter.printFieldDeclaration(parameter.asType().toString(), parameter.getSimpleName().toString());
                        invocationArgs.add(parameter.getSimpleName().toString());
                    }
                }else {
                    for (var property : messageElement.properties()) {
                        builderClassWriter.printFieldDeclaration(property.type().implementationType().toString(), property.name());
                        invocationArgs.add(property.name());
                    }
                }

                // Separate fields and constructors
                compilationUnitWriter.printClassSeparator();

                // Write the builder's constructor
                try(var builderConstructorWriter = builderClassWriter.printConstructorDeclaration(simpleGeneratedClassName)) {
                    if (builderElement == null) {
                        // Assign each field in the builder to its default value
                        for (var property : messageElement.properties()) {
                            builderConstructorWriter.printFieldAssignment(property.name(), property.type().defaultValue());
                        }
                    }
                }

                // Write the setters for each field in the builder
                if(builderElement != null) {
                    for(var parameter : builderElement.parameters()) {
                        var fieldName = parameter.getSimpleName().toString();
                        writeBuilderSetter(builderClassWriter, fieldName, fieldName, parameter.asType(), simpleGeneratedClassName);
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
                                    builderClassWriter,
                                    property.name(),
                                    fieldValue,
                                    override.parameterType(),
                                    simpleGeneratedClassName
                            );
                            done |= override.behaviour() == ProtobufDeserializer.BuilderBehaviour.OVERRIDE;
                        }

                        if(!done) { // If there are no overrides, don't print the default setter
                            writeBuilderSetter(
                                    builderClassWriter,
                                    property.name(),
                                    property.name(),
                                    property.type().implementationType(),
                                    simpleGeneratedClassName
                            );
                        }
                    }
                }

                // Print the build method
                var resultQualifiedName = messageElement.element().getQualifiedName();
                try(var buildMethodWriter = builderClassWriter.printMethodDeclaration(resultQualifiedName.toString(), "build")) {
                    var invocationArgsJoined = String.join(", ", invocationArgs);
                    var invocation = builderElement == null ? "new %s(%s)".formatted(resultQualifiedName, invocationArgsJoined) : "%s.%s(%s)".formatted(resultQualifiedName, builderElement.delegate().getSimpleName(), invocationArgsJoined);
                    buildMethodWriter.printReturn(invocation);
                }
            }
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

    private void writeBuilderSetter(NestedClassWriter writer, String fieldName, String fieldValue, TypeMirror fieldType, String className) {
        try(var setterWriter = writer.printMethodDeclaration(className, fieldName, "%s %s".formatted(fieldType, fieldName))) {
            setterWriter.printFieldAssignment("this." + fieldName, fieldValue);
            setterWriter.printReturn("this");
        }
    }
}
