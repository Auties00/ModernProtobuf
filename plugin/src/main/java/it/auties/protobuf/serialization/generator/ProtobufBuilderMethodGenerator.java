package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.*;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.serialization.model.ProtobufBuilderElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class ProtobufBuilderMethodGenerator extends ProtobufClassGenerator {
    public ProtobufBuilderMethodGenerator(Filer filer) {
        super(filer);
    }

    public void createClass(String packageName, ProtobufObjectElement objectElement, ProtobufBuilderElement builderElement) throws IOException {
        // Names
        Objects.requireNonNull(builderElement);
        var simpleGeneratedClassName = getGeneratedClassNameByName(objectElement.typeElement(), builderElement.name());
        var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;

        // Create class type spec
        var classBuilder = TypeSpec.classBuilder(simpleGeneratedClassName)
                .addModifiers(Modifier.PUBLIC);

        // Write the fields of the builder and collect them
        var invocationArgs = new ArrayList<String>();
        if (builderElement != null) {
            for (var parameter : builderElement.parameters()) {
                var fieldType = TypeName.get(parameter.asType());
                var fieldName = parameter.getSimpleName().toString();

                classBuilder.addField(FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE).build());
                invocationArgs.add(fieldName);
            }
        } else {
            for (var property : objectElement.properties()) {
                if (property.synthetic()) {
                    continue;
                }

                var fieldType = TypeName.get(property.type().descriptorElementType());
                var fieldName = property.name();

                classBuilder.addField(FieldSpec.builder(fieldType, fieldName, Modifier.PRIVATE).build());
                invocationArgs.add(fieldName);
            }
        }

        // Write the builder's constructor
        var constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        if (builderElement == null) {
            // Assign each field in the builder to its default value
            for (var property : objectElement.properties()) {
                if (property.synthetic()) {
                    continue;
                }

                constructorBuilder.addStatement("this.$L = $L", property.name(), property.type().descriptorDefaultValue());
            }
        }

        classBuilder.addMethod(constructorBuilder.build());

        // Write the setters for each field in the builder
        if (builderElement != null) {
            for (var parameter : builderElement.parameters()) {
                var fieldName = parameter.getSimpleName().toString();
                var setter = createBuilderSetter(
                        fieldName,
                        fieldName,
                        parameter.asType(),
                        simpleGeneratedClassName
                );
                classBuilder.addMethod(setter);
            }
        } else {
            for (var property : objectElement.properties()) {
                if (property.synthetic()) {
                    continue;
                }

                var deserializers = property.type().deserializers();
                var hasOverride = false;
                if (property.type() instanceof ProtobufPropertyType.NormalType) {
                    for (var i = 0; i < deserializers.size(); i++) {
                        var deserializer = deserializers.get(i);
                        if (deserializer.behaviour() == ProtobufDeserializer.BuilderSetterMethod.DISCARD) {
                            continue;
                        }

                        if (hasOverride) {
                            hasOverride = deserializer.behaviour() == ProtobufDeserializer.BuilderSetterMethod.OVERRIDE;
                            continue;
                        }

                        var value = property.name();
                        for (var j = i; j < deserializers.size(); j++) {
                            var override = deserializers.get(j);
                            value = "%s.%s(%s)".formatted(
                                    override.delegate().ownerName(),
                                    override.delegate().name(),
                                    value
                            );
                        }

                        var setter = createBuilderSetter(
                                property.name(),
                                value,
                                deserializer.parameterType(),
                                simpleGeneratedClassName
                        );
                        classBuilder.addMethod(setter);
                        hasOverride = deserializer.behaviour() == ProtobufDeserializer.BuilderSetterMethod.OVERRIDE;
                    }
                }

                if (!hasOverride) {
                    var setter = createBuilderSetter(
                            property.name(),
                            property.name(),
                            property.type().descriptorElementType(),
                            simpleGeneratedClassName
                    );
                    classBuilder.addMethod(setter);
                }
            }
        }

        // Print the build method
        var resultType = TypeName.get(objectElement.typeElement().asType());
        var buildMethodBuilder = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(resultType);

        var invocationArgsJoined = String.join(", ", invocationArgs);
        var builderDelegate = objectElement.deserializer();
        var unknownFieldsValue = objectElement.unknownFieldsElement().isEmpty() ? "" : ", " + objectElement.unknownFieldsElement().get().defaultValue();

        if (builderDelegate.isEmpty() && (builderElement == null || builderElement.delegate().getKind() == ElementKind.CONSTRUCTOR)) {
            buildMethodBuilder.addStatement("return new $T($L$L)", resultType, invocationArgsJoined, unknownFieldsValue);
        } else {
            var methodName = builderElement != null ? builderElement.delegate().getSimpleName() : builderDelegate.get().name();
            buildMethodBuilder.addStatement("return $T.$L($L$L)", resultType, methodName, invocationArgsJoined, unknownFieldsValue);
        }

        classBuilder.addMethod(buildMethodBuilder.build());

        // Create JavaFile and write to filer
        var packageNameStr = packageName != null ? packageName.getQualifiedName().toString() : "";
        var javaFile = JavaFile.builder(packageNameStr, classBuilder.build())
                .build();

        javaFile.writeTo(filer);
    }

    private MethodSpec createBuilderSetter(String fieldName, String fieldValue, TypeMirror fieldType, String className) {
        var parameterType = TypeName.get(fieldType);
        var returnType = ClassName.bestGuess(className);

        return MethodSpec.methodBuilder(fieldName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(parameterType, fieldName)
                .returns(returnType)
                .addStatement("this.$L = $L", fieldName, fieldValue)
                .addStatement("return this")
                .build();
    }
}