package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.*;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.ArrayList;

public class ProtobufBuilderTypeGenerator extends ProtobufClassGenerator {
    public ProtobufBuilderTypeGenerator(Filer filer) {
        super(filer);
    }

    public void createClass(String packageName, ProtobufObjectElement objectElement) throws IOException {
        // Create a class type spec
        var className = getGeneratedClassNameBySuffix(objectElement.typeElement(), "Builder");
        var classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC);

        // Write the fields of the builder and collect them
        var invocationArgs = new ArrayList<String>();
        for (var property : objectElement.properties()) {
            if (property.synthetic()) {
                continue;
            }

            var fieldType = property.type().descriptorElementType();
            var fieldName = property.name();
            var fieldSpec = createField(fieldType, fieldName);
            classBuilder.addField(fieldSpec);
            invocationArgs.add(fieldName);
        }

        // Write the builder's constructor
        var constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        for (var property : objectElement.properties()) {
            if (property.synthetic()) {
                continue;
            }

            var name = property.name();
            var defaultValue = property.type().descriptorDefaultValue();
            constructorBuilder.addStatement("this.$L = $L", name, defaultValue);
        }
        classBuilder.addMethod(constructorBuilder.build());

        // Write the setters for each field in the builder
        for (var property : objectElement.properties()) {
            if (property.synthetic()) {
                continue;
            }

            var deserializers = property.type().deserializers();
            if (property.type() instanceof ProtobufPropertyType.NormalType) {
                for (var i = isGroupOrMessage(property) ? 1 : 0; i < deserializers.size(); i++) {
                    var deserializer = deserializers.get(i);
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
                            className
                    );
                    classBuilder.addMethod(setter);
                }
            }
            var setter = createBuilderSetter(
                    property.name(),
                    property.name(),
                    property.type().descriptorElementType(),
                    className
            );
            classBuilder.addMethod(setter);
        }

        // Print the build method
        var buildMethodBuilder = createBuildMethod(objectElement, invocationArgs);
        classBuilder.addMethod(buildMethodBuilder.build());

        // Create a java source file
        var javaFile = JavaFile.builder(packageName, classBuilder.build())
                .build();
        javaFile.writeTo(filer);
    }

    private boolean isGroupOrMessage(ProtobufPropertyElement property) {
        return property.type().protobufType() == ProtobufType.MESSAGE
                || property.type().protobufType() == ProtobufType.GROUP;
    }

    private MethodSpec.Builder createBuildMethod(ProtobufObjectElement objectElement, ArrayList<String> invocationArgs) {
        var resultType = TypeName.get(objectElement.typeElement().asType());
        var buildMethodBuilder = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(resultType);
        var invocationArgsJoined = String.join(", ", invocationArgs);
        var builderDelegate = objectElement.deserializer();
        var unknownFieldsValue = objectElement.unknownFieldsElement().isEmpty() ? "" : ", " + objectElement.unknownFieldsElement().get().defaultValue();
        if (builderDelegate.isEmpty()) {
            buildMethodBuilder.addStatement("return new $T($L$L)", resultType, invocationArgsJoined, unknownFieldsValue);
        } else {
            var methodName = builderDelegate.get().name();
            buildMethodBuilder.addStatement("return $T.$L($L$L)", resultType, methodName, invocationArgsJoined, unknownFieldsValue);
        }
        return buildMethodBuilder;
    }

    private FieldSpec createField(TypeMirror type, String name) {
        var typeName = TypeName.get(type);
        return FieldSpec.builder(typeName, name)
                .addModifiers(Modifier.PRIVATE)
                .build();
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