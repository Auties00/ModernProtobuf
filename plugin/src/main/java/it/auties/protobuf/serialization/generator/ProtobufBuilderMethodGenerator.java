package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.*;
import it.auties.protobuf.serialization.model.ProtobufBuilderElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.ArrayList;

public class ProtobufBuilderMethodGenerator extends ProtobufClassGenerator {
    public ProtobufBuilderMethodGenerator(Filer filer) {
        super(filer);
    }

    public void createClass(String packageName, ProtobufObjectElement objectElement, ProtobufBuilderElement builderElement) throws IOException {
        // Create a class type spec
        var className = getGeneratedClassNameByName(objectElement.typeElement(), builderElement.name());
        var classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC);

        // Write the fields of the builder and collect them
        var invocationArgs = new ArrayList<String>();
        for (var parameter : builderElement.parameters()) {
            var fieldType = parameter.asType();
            var fieldName = parameter.getSimpleName().toString();
            classBuilder.addField(createField(fieldType, fieldName));
            invocationArgs.add(fieldName);
        }

        // Write the builder's constructor
        var constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        classBuilder.addMethod(constructorBuilder.build());

        // Write the setters for each field in the builder
        for (var parameter : builderElement.parameters()) {
            var fieldName = parameter.getSimpleName().toString();
            var setter = createBuilderSetter(
                    fieldName,
                    fieldName,
                    parameter.asType(),
                    className
            );
            classBuilder.addMethod(setter);
        }

        // Write the build method
        var buildMethodBuilder = createBuildMethod(objectElement, builderElement, invocationArgs);
        classBuilder.addMethod(buildMethodBuilder.build());

        // Create a java source file
        var javaFile = JavaFile.builder(packageName, classBuilder.build())
                .build();
        javaFile.writeTo(filer);
    }

    private MethodSpec.Builder createBuildMethod(ProtobufObjectElement objectElement, ProtobufBuilderElement builderElement, ArrayList<String> invocationArgs) {
        var resultType = TypeName.get(objectElement.typeElement().asType());
        var buildMethodBuilder = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(resultType);
        var invocationArgsJoined = String.join(", ", invocationArgs);
        var builderDelegate = objectElement.deserializer();
        var unknownFieldsValue = objectElement.unknownFieldsElement().isEmpty() ? "" : ", " + objectElement.unknownFieldsElement().get().defaultValue();
        if (builderDelegate.isEmpty() && builderElement.delegate().getKind() == ElementKind.CONSTRUCTOR) {
            buildMethodBuilder.addStatement("return new $T($L$L)", resultType, invocationArgsJoined, unknownFieldsValue);
        } else {
            var methodName = builderElement.delegate().getSimpleName();
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