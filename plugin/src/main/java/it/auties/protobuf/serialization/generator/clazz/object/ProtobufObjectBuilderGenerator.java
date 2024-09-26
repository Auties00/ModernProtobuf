package it.auties.protobuf.serialization.generator.clazz.object;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.serialization.generator.clazz.ProtobufClassGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufBuilderElement;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.ArrayList;

public class ProtobufObjectBuilderGenerator extends ProtobufClassGenerator {
    public ProtobufObjectBuilderGenerator(Filer filer) {
        super(filer);
    }

    public void createClass(ProtobufObjectElement objectElement, ProtobufBuilderElement builderElement, PackageElement packageName) throws IOException {
        // Names
        var simpleGeneratedClassName = builderElement != null ? getGeneratedClassNameByName(objectElement.element(), builderElement.name()) : getGeneratedClassNameBySuffix(objectElement.element(), "Builder");
        var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;
        var sourceFile = filer.createSourceFile(qualifiedGeneratedClassName);

        // Declare a new compilation unit
        try (var compilationUnitWriter = new JavaWriter.CompilationUnit(sourceFile.openWriter())) {
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
                    for (var property : objectElement.properties()) {
                        if(property.synthetic()) {
                            continue;
                        }

                        builderClassWriter.printFieldDeclaration(property.type().descriptorElementType().toString(), property.name());
                        invocationArgs.add(property.name());
                    }
                }

                // Separate fields and constructors
                compilationUnitWriter.printSeparator();

                // Write the builder's constructor
                try(var builderConstructorWriter = builderClassWriter.printConstructorDeclaration(simpleGeneratedClassName)) {
                    if (builderElement == null) {
                        // Assign each field in the builder to its default value
                        for (var property : objectElement.properties()) {
                            if(property.synthetic()) {
                                continue;
                            }

                            builderConstructorWriter.printFieldAssignment(property.name(), property.type().descriptorDefaultValue());
                        }
                    }
                }

                // Write the setters for each field in the builder
                if(builderElement != null) {
                    for(var parameter : builderElement.parameters()) {
                        var fieldName = parameter.getSimpleName().toString();
                        writeBuilderSetter(
                                builderClassWriter,
                                fieldName,
                                fieldName,
                                parameter.asType(),
                                simpleGeneratedClassName
                        );
                    }
                }else {
                    for(var property : objectElement.properties()) {
                        if(property.synthetic()) {
                            continue;
                        }

                        var deserializers = property.type().deserializers();
                        var hasOverride = false;
                        if(property.type() instanceof ProtobufPropertyType.NormalType) {
                            for (var i = 0; i < deserializers.size(); i++) {
                                var deserializer = deserializers.get(i);
                                if (deserializer.behaviour() == ProtobufDeserializer.BuilderBehaviour.DISCARD) {
                                    continue;
                                }

                                if (hasOverride) {
                                    hasOverride = deserializer.behaviour() == ProtobufDeserializer.BuilderBehaviour.OVERRIDE;
                                    continue;
                                }

                                var value = property.name();
                                for (var j = i; j < deserializers.size(); j++) {
                                    var override = deserializers.get(j);
                                    var enclosingElement = (TypeElement) override.delegate().getEnclosingElement();
                                    value = "%s.%s(%s)".formatted(enclosingElement.getQualifiedName(), override.delegate().getSimpleName(), value);
                                }

                                writeBuilderSetter(
                                        builderClassWriter,
                                        property.name(),
                                        value,
                                        deserializer.parameterType(),
                                        simpleGeneratedClassName
                                );
                                hasOverride = deserializer.behaviour() == ProtobufDeserializer.BuilderBehaviour.OVERRIDE;
                            }
                        }

                        if(!hasOverride) {
                            writeBuilderSetter(
                                    builderClassWriter,
                                    property.name(),
                                    property.name(),
                                    property.type().descriptorElementType(),
                                    simpleGeneratedClassName
                            );
                        }
                    }
                }

                // Print the build method
                var resultQualifiedName = objectElement.element().getQualifiedName();
                try(var buildMethodWriter = builderClassWriter.printMethodDeclaration(resultQualifiedName.toString(), "build")) {
                    var invocationArgsJoined = String.join(", ", invocationArgs);
                    var builderDelegate = objectElement.deserializer();
                    var unknownFieldsValue = objectElement.unknownFieldsElement().isEmpty() ? "" : ", " + objectElement.unknownFieldsElement().get().defaultValue();
                    if (builderDelegate.isEmpty() && (builderElement == null || builderElement.delegate().getKind() == ElementKind.CONSTRUCTOR)) {
                        buildMethodWriter.printReturn("new %s(%s%s)".formatted(resultQualifiedName, invocationArgsJoined, unknownFieldsValue));
                    } else {
                        var methodName = builderElement != null ? builderElement.delegate().getSimpleName() : builderDelegate.get().getSimpleName();
                        buildMethodWriter.printReturn("%s.%s(%s%s)".formatted(resultQualifiedName, methodName, invocationArgsJoined, unknownFieldsValue));
                    }
                }
            }
        }
    }

    private void writeBuilderSetter(ClassWriter writer, String fieldName, String fieldValue, TypeMirror fieldType, String className) {
        try(var setterWriter = writer.printMethodDeclaration(className, fieldName, "%s %s".formatted(fieldType, fieldName))) {
            setterWriter.printFieldAssignment("this." + fieldName, fieldValue);
            setterWriter.printReturn("this");
        }
    }
}
