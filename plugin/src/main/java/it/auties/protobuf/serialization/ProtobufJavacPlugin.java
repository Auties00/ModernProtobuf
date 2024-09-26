package it.auties.protobuf.serialization;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.builtin.*;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.generator.clazz.group.ProtobufRawGroupSpecGenerator;
import it.auties.protobuf.serialization.generator.clazz.object.ProtobufObjectBuilderGenerator;
import it.auties.protobuf.serialization.generator.clazz.object.ProtobufObjectSpecGenerator;
import it.auties.protobuf.serialization.generator.method.ProtobufMethodGenerator;
import it.auties.protobuf.serialization.generator.method.deserialization.ProtobufDeserializationGenerator;
import it.auties.protobuf.serialization.generator.method.serialization.ProtobufSerializationGenerator;
import it.auties.protobuf.serialization.model.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.model.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.model.object.ProtobufEnumMetadata;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.object.ProtobufUnknownFieldsElement;
import it.auties.protobuf.serialization.model.property.ProtobufGroupPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.Checks;
import it.auties.protobuf.serialization.support.Messages;
import it.auties.protobuf.serialization.support.Types;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@SupportedAnnotationTypes({
        "it.auties.protobuf.annotation.ProtobufProperty",
        "it.auties.protobuf.annotation.ProtobufSerializer",
        "it.auties.protobuf.annotation.ProtobufDeserializer",
        "it.auties.protobuf.annotation.ProtobufObject",
        "it.auties.protobuf.annotation.ProtobufBuilder",
        "it.auties.protobuf.annotation.ProtobufEnumIndex"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ProtobufJavacPlugin extends AbstractProcessor {
    // Mirrored list of default mixins
    private static final Class<?>[] DEFAULT_MIXINS = {
            ProtobufAtomicMixin.class,
            ProtobufOptionalMixin.class,
            ProtobufUUIDMixin.class,
            ProtobufURIMixin.class,
            ProtobufRepeatedMixin.class,
            ProtobufMapMixin.class,
            ProtobufFutureMixin.class,
            ProtobufLazyMixin.class
    };

    // Useful utility classes to perform checks and print errors/warnings
    private Trees trees;
    private Types types;
    private Messages messages;
    private Checks checks;

    // A graph-like representation of converters to speed up the discovering process
    private ProtobufConverterGraph serializersGraph;
    private ProtobufConverterGraph deserializersGraph;

    // This cache is needed because if a raw groups uses itself as a type in one of its properties
    // Then computing the properties in its serializer would cause a StackOverFlow
    // This is because the groupProperties are defined in the ProtobufSerializer
    private Map<ExecutableElement, Map<Integer, ProtobufGroupPropertyElement>> rawGroupPropertiesMap;

    // Called when the annotation processor is initialized
    @Override
    public synchronized void init(ProcessingEnvironment wrapperProcessingEnv) {
        var unwrappedProcessingEnv = unwrapProcessingEnv(wrapperProcessingEnv);
        super.init(unwrappedProcessingEnv);
        this.trees = Trees.instance(processingEnv);
        this.types = new Types(processingEnv);
        this.messages = new Messages(processingEnv);
        this.checks = new Checks(types, messages);
        this.serializersGraph = new ProtobufConverterGraph(types);
        this.deserializersGraph = new ProtobufConverterGraph(types);
        this.rawGroupPropertiesMap = new HashMap<>();
    }

    // Unwrap the processing environment
    // Needed if running in IntelliJ
    private ProcessingEnvironment unwrapProcessingEnv(ProcessingEnvironment wrapper) {
        try {
            var apiWrappers = wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
            var unwrapMethod = apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            return (ProcessingEnvironment) unwrapMethod.invoke(null, ProcessingEnvironment.class, wrapper);
        } catch (ReflectiveOperationException exception) {
            return wrapper;
        }
    }

    // Called when the annotation processor starts processing data
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Make sure that annotations are not being used in the wrong scope
        checks.runChecks(roundEnv);

        // Build graph representation of serializers and deserializers
        buildConvertersGraph(roundEnv);

        // Do the actual processing of the annotations
        processObjects(roundEnv);

        return true;
    }

    private void buildConvertersGraph(RoundEnvironment roundEnv) {
        var intType = types.getType(int.class);
        var inputStreamType = types.getType(ProtobufInputStream.class);
        var outputStreamType = types.getType(ProtobufOutputStream.class);
        var serializedGroupType = types.getType(ProtobufType.GROUP.serializedType());
        for(var serializer : roundEnv.getElementsAnnotatedWith(ProtobufSerializer.class)) {
            if(serializer instanceof ExecutableElement serializerMethod) {
                linkSerializer(serializerMethod);

                var serializerAnnotation = serializer.getAnnotation(ProtobufSerializer.class);
                for(var rawGroupProperty : serializerAnnotation.groupProperties()) {
                    linkMixins(types.getMirroredTypes(rawGroupProperty::mixins));
                }

                if(serializerAnnotation.groupProperties().length != 0) {
                    var groupType = serializer.getEnclosingElement().asType();
                    var specName = ProtobufMethodGenerator.getSpecFromObject(groupType);
                    var syntheticSerializer = types.createMethodStub(specName, ProtobufSerializationGenerator.METHOD_NAME, types.voidType(), intType, types.rawGroupType(), outputStreamType);
                    serializersGraph.link(types.rawGroupType(), serializedGroupType, groupType, syntheticSerializer);
                    var syntheticDeserializer = types.createMethodStub(specName, ProtobufDeserializationGenerator.METHOD_NAME, types.rawGroupType(), intType, inputStreamType);
                    deserializersGraph.link(serializedGroupType, types.rawGroupType(), groupType, syntheticDeserializer);
                }
            }
        }
        for(var deserializer : roundEnv.getElementsAnnotatedWith(ProtobufDeserializer.class)) {
            if(deserializer instanceof ExecutableElement deserializerMethod) {
                linkDeserializer(deserializerMethod);
            }
        }
        for(var property : roundEnv.getElementsAnnotatedWith(ProtobufProperty.class)) {
            var propertyMixins = types.getMixins(property.getAnnotation(ProtobufProperty.class));
            linkMixins(propertyMixins);
        }
        for(var getter : roundEnv.getElementsAnnotatedWith(ProtobufGetter.class)) {
            var getterMixins = types.getMixins(getter.getAnnotation(ProtobufGetter.class));
            linkMixins(getterMixins);
        }
        for(var unknownFields : roundEnv.getElementsAnnotatedWith(ProtobufUnknownFields.class)) {
            var unknownFieldsMixins = types.getMixins(unknownFields.getAnnotation(ProtobufUnknownFields.class));
            linkMixins(unknownFieldsMixins);
        }
        var serializedMessageType = types.getType(ProtobufType.MESSAGE.serializedType());
        for(var message : roundEnv.getElementsAnnotatedWith(ProtobufMessage.class)) {
            var specName = ProtobufMethodGenerator.getSpecFromObject(message.asType());
            var serializer = types.createMethodStub(specName, ProtobufSerializationGenerator.METHOD_NAME, serializedMessageType, message.asType(), outputStreamType);
            serializersGraph.link(message.asType(), serializedMessageType, null, serializer);
            var deserializer = types.createMethodStub(specName, ProtobufDeserializationGenerator.METHOD_NAME, message.asType(), serializedMessageType);
            deserializersGraph.link(serializedMessageType, message.asType(), null, deserializer);
        }
        for(var group : roundEnv.getElementsAnnotatedWith(ProtobufGroup.class)) {
            var specName = ProtobufMethodGenerator.getSpecFromObject(group.asType());
            var serializer = types.createMethodStub(specName, ProtobufSerializationGenerator.METHOD_NAME, serializedGroupType, intType, group.asType(), outputStreamType);
            serializersGraph.link(group.asType(), serializedGroupType, null, serializer);
            var deserializer = types.createMethodStub(specName, ProtobufDeserializationGenerator.METHOD_NAME, group.asType(), intType, serializedGroupType);
            deserializersGraph.link(serializedGroupType, group.asType(), null, deserializer);
        }
        var serializedEnumType = types.getType(ProtobufType.ENUM.serializedType());
        for(var enumeration : roundEnv.getElementsAnnotatedWith(ProtobufEnum.class)) {
            var specName = ProtobufMethodGenerator.getSpecFromObject(enumeration.asType());
            var serializer = types.createMethodStub(specName, ProtobufSerializationGenerator.METHOD_NAME, serializedEnumType, enumeration.asType());
            serializersGraph.link(enumeration.asType(), serializedEnumType, null, serializer);
            var deserializer = types.createMethodStub(specName, ProtobufDeserializationGenerator.METHOD_NAME, enumeration.asType(), serializedEnumType);
            deserializersGraph.link(serializedEnumType, enumeration.asType(), null, deserializer);
        }
    }

    private void linkSerializer(ExecutableElement serializerMethod) {
        var from = !serializerMethod.getParameters().isEmpty() ? serializerMethod.getParameters().getFirst().asType() : serializerMethod.getEnclosingElement().asType();
        var to = serializerMethod.getReturnType();
        serializersGraph.link(from, to, null, serializerMethod);
    }

    private void linkDeserializer(ExecutableElement deserializerMethod) {
        var from = deserializerMethod.getParameters().getFirst().asType();
        var to = deserializerMethod.getReturnType();
        deserializersGraph.link(from, to, null, deserializerMethod);
    }

    private void linkMixins(List<TypeElement> mixins) {
        for(var mixin : mixins) {
            for(var element : mixin.getEnclosedElements()) {
                if(element instanceof ExecutableElement method) {
                    if(method.getAnnotation(ProtobufSerializer.class) != null) {
                        linkSerializer(method);
                    }else if(method.getAnnotation(ProtobufDeserializer.class) != null) {
                        linkDeserializer(method);
                    }
                }
            }
        }
    }

    // This is where the actual processing happens
    private void processObjects(RoundEnvironment roundEnv) {
        TypeElement currentElement = null;
        try {
            var objects = getProtobufObjects(roundEnv);
            for(var object : objects) {
                currentElement = object;
                var result = processElement(object);
                if (result.isEmpty()) {
                    continue;
                }

                var packageName = processingEnv.getElementUtils().getPackageOf(result.get().element());
                var specVisitor = new ProtobufObjectSpecGenerator(processingEnv.getFiler());
                specVisitor.createClass(result.get(), packageName);
                if(result.get().isEnum()){
                    continue;
                }

                var buildVisitor = new ProtobufObjectBuilderGenerator(processingEnv.getFiler());
                buildVisitor.createClass(result.get(), null, packageName);
                for (var builder : result.get().builders()) {
                    buildVisitor.createClass(result.get(), builder, packageName);
                }
            }
        }catch (IOException throwable) {
            messages.printError("An error occurred while processing protobuf: " + Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getName()), currentElement);
        }
    }

    private List<TypeElement> getProtobufObjects(RoundEnvironment roundEnv) {
        return Stream.of(roundEnv.getElementsAnnotatedWith(ProtobufMessage.class), roundEnv.getElementsAnnotatedWith(ProtobufGroup.class), roundEnv.getElementsAnnotatedWith(ProtobufEnum.class))
                .flatMap(Collection::stream)
                .filter(entry -> entry instanceof TypeElement)
                .map(entry -> (TypeElement) entry)
                .toList();
    }

    private Optional<ProtobufObjectElement> processElement(TypeElement object) {
        if(object.getModifiers().contains(Modifier.ABSTRACT)) {
            return Optional.empty();
        }

        return switch (object.getKind()) {
            case ENUM -> processEnum(object);
            case RECORD, CLASS -> processMessage(object);
            default -> Optional.empty();
        };
    }

    private Optional<ProtobufObjectElement> processMessage(TypeElement message) {
        var builderDelegate = getMessageDeserializer(message);
        var isGroup = message.getAnnotation(ProtobufGroup.class) != null;
        var messageElement = new ProtobufObjectElement(message, null, builderDelegate.orElse(null), isGroup);
        processMessage(messageElement, messageElement.element());
        if (hasPropertiesConstructor(messageElement)) {
            return Optional.of(messageElement);
        }

        messages.printError("Missing protobuf constructor: a protobuf message must provide a constructor that takes only its properties, following their declaration order, and, if present, its unknown fields wrapper as parameters", messageElement.element());
        return Optional.empty();
    }

    private Optional<ExecutableElement> getMessageDeserializer(TypeElement message) {
        return message.getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof ExecutableElement method && method.getAnnotation(ProtobufDeserializer.class) != null)
                .map(entry -> (ExecutableElement) entry)
                .reduce((first, second) -> {
                    messages.printError("Duplicated protobuf builder delegate: a message should provide only one method annotated with @ProtobufDeserializer", second);
                    return first;
                });
    }

    // We could run directly the processing on fields and methods
    // But supporting standalone getters requires either to run fields before methods or to run additional checks later
    private void processMessage(ProtobufObjectElement messageElement, TypeElement typeElement) {
        getSuperClass(typeElement)
                .ifPresent(superClass -> processMessage(messageElement, superClass));
        var fields = new ArrayList<VariableElement>();
        var methods = new ArrayList<ExecutableElement>();
        for (var entry : typeElement.getEnclosedElements()) {
            switch (entry) {
                case VariableElement variableElement -> fields.add(variableElement);
                case ExecutableElement executableElement -> methods.add(executableElement);
                case null, default -> {}
            }
        }
        for(var field : fields) {
            handleField(messageElement, field);
        }
        for(var method : methods) {
            handleMethod(messageElement, method);
        }
    }

    private Optional<TypeElement> getSuperClass(TypeElement typeElement) {
        var superClass = typeElement.getSuperclass();
        if(superClass == null || superClass.getKind() == TypeKind.NONE) {
            return Optional.empty();
        }

        var superClassElement = ((DeclaredType) superClass).asElement();
        return Optional.of((TypeElement) superClassElement);
    }

    private void handleMethod(ProtobufObjectElement messageElement, ExecutableElement executableElement) {
        var builder = executableElement.getAnnotation(ProtobufBuilder.class);
        if(builder != null) {
            messageElement.addBuilder(builder.className(), executableElement.getParameters(), executableElement);
            return;
        }

        var getter = executableElement.getAnnotation(ProtobufGetter.class);
        if(getter != null) {
            handleGetter(messageElement, executableElement, getter);
        }
    }

    private void handleGetter(ProtobufObjectElement messageElement, ExecutableElement executableElement, ProtobufGetter getter) {
        if(hasMatchedProperty(messageElement, getter)) {
            if(getter.type() != ProtobufType.UNKNOWN) {
                messages.printError("Invalid metadata: only elements annotated with @ProtobufGetter that represent a standalone property can specify a type", executableElement);
            }else if(getter.packed()) {
                messages.printError("Invalid metadata: only elements annotated with @ProtobufGetter that represent a standalone property can specify whether they are packed", executableElement);
            }else if(isNonDefaultMixin(getter)) {
                messages.printError("Invalid metadata: only elements annotated with @ProtobufGetter that represent a standalone property can specify mixins", executableElement);
            }

            return;
        }

        var property = types.getProperty(getter);
        if(getter.type() == ProtobufType.UNKNOWN) {
            messages.printError("Type error: standalone property getters must specify a valid protobuf type", executableElement);
            return;
        }

        if(getter.packed() && !checks.isValidPackedProperty(executableElement, property)) {
            return;
        }

        var type = getPropertyType(executableElement, executableElement.getReturnType(), executableElement.getReturnType(), property, null, null);
        if(type.isEmpty()) {
            return;
        }

        var syntheticPropertyName = types.getPropertyName(executableElement.getSimpleName().toString());
        if(messageElement.isNameDisallowed(syntheticPropertyName)) {
            messages.printError("Restricted message property name: %s is not allowed as it's marked as reserved".formatted(syntheticPropertyName), executableElement);
        }

        if(messageElement.isIndexDisallowed(property.index())) {
            messages.printError("Restricted message property index: %s is not allowed as it's marked as reserved".formatted(property.index()), executableElement);
        }

        var error = messageElement.addProperty(executableElement, executableElement, type.get(), property);
        if(error.isEmpty()) {
            return;
        }

        messages.printError("Duplicated message property: %s and %s with index %s".formatted(executableElement.getSimpleName(), error.get().name(), getter.index()), executableElement);
    }

    private boolean isNonDefaultMixin(ProtobufGetter getter) {
        var mixins = types.getMixins(getter);
        if(mixins.size() != DEFAULT_MIXINS.length) {
            return true;
        }

        var mixinsIterator = mixins.iterator();
        for (Class<?> expectedMixin : DEFAULT_MIXINS) {
            var actualMixin = mixinsIterator.next();
            if (!actualMixin.getQualifiedName().contentEquals(expectedMixin.getCanonicalName())) {
                return true;
            }
        }

        return false;
    }

    private boolean hasMatchedProperty(ProtobufObjectElement messageElement, ProtobufGetter getter) {
        return messageElement.properties()
                .stream()
                .anyMatch(entry -> !(entry.type().descriptorElementType() instanceof ExecutableElement) && entry.index() == getter.index());
    }

    private boolean hasPropertiesConstructor(ProtobufObjectElement message) {
        var unknownFieldsType = message.unknownFieldsElement()
                .orElse(null);
        var properties = message.properties()
                .stream()
                .filter(property -> !property.synthetic())
                .toList();
        return message.element()
                .getEnclosedElements()
                .stream()
                .filter(entry -> entry.getKind() == ElementKind.CONSTRUCTOR)
                .map(entry -> (ExecutableElement) entry)
                .anyMatch(constructor -> {
                    var constructorParameters = constructor.getParameters();
                    if(properties.size() + (unknownFieldsType != null ? 1 : 0) != constructorParameters.size()) {
                        return false;
                    }

                    var propertiesIterator = properties.iterator();
                    var constructorParametersIterator = constructorParameters.iterator();
                    var foundUnknownFieldsParam = false;
                    while (propertiesIterator.hasNext() && constructorParametersIterator.hasNext()) {
                        var property = propertiesIterator.next();
                        var constructorParameter = constructorParametersIterator.next();
                        if(unknownFieldsType != null && types.isAssignable(constructorParameter.asType(), property.type().descriptorElementType())) {
                            if(foundUnknownFieldsParam) {
                                messages.printError("Duplicated protobuf unknown fields parameter: a protobuf constructor should provide only one parameter whose type can be assigned to the field annotated with @ProtobufUnknownFields", constructorParameter);
                            }

                            foundUnknownFieldsParam = true;
                        }else if(!types.isAssignable(property.type().descriptorElementType(), constructorParameter.asType())) {
                            return false;
                        }
                    }

                    return unknownFieldsType == null || foundUnknownFieldsParam;
                });
    }

    private void handleField(ProtobufObjectElement messageElement, VariableElement variableElement) {
        var propertyAnnotation = variableElement.getAnnotation(ProtobufProperty.class);
        if(propertyAnnotation != null) {
            processMessageProperty(messageElement, variableElement, propertyAnnotation);
            return;
        }

        var unknownFieldsAnnotation = variableElement.getAnnotation(ProtobufUnknownFields.class);
        if(unknownFieldsAnnotation == null) {
            return;
        }

        if(messageElement.unknownFieldsElement().isPresent()) {
            messages.printError("Duplicated protobuf unknown fields: a message should provide only one method field annotated with @ProtobufUnknownFields", variableElement);
            return;
        }

        var unknownFields = processUnknownFieldsField(variableElement, unknownFieldsAnnotation);
        if(unknownFields.isEmpty()) {
            return;
        }

        messageElement.setUnknownFieldsElement(unknownFields.get());
    }

    private Optional<ProtobufUnknownFieldsElement> processUnknownFieldsField(VariableElement variableElement, ProtobufUnknownFields unknownFieldsAnnotation) {
        var unknownFieldsType = variableElement.asType();
        if(!(unknownFieldsType instanceof DeclaredType unknownFieldsDeclaredType)) {
            messages.printError("Type error: variables annotated with @ProtobufUnknownFields must have an object type", variableElement);
            return Optional.empty();
        }

        var mixins = types.getMixins(unknownFieldsAnnotation);
        var setter = findUnknownFieldsSetterInType(unknownFieldsDeclaredType);
        if(setter != null) {
            return checkUnknownFieldsSetter(variableElement, setter, false)
                    .map(setterElement -> createUnknownFieldsElement(variableElement, unknownFieldsDeclaredType, setterElement, unknownFieldsType, mixins));
        }

        var setterFromMixin = findUnknownFieldsSetterInMixins(variableElement, unknownFieldsType, mixins);
        if(setterFromMixin == null) {
            messages.printError("Type error: cannot find a @ProtobufUnknownFields.Setter for the provided type", variableElement);
            return Optional.empty();
        }

        return checkUnknownFieldsSetter(variableElement, setterFromMixin, true)
                .map(setterElement -> createUnknownFieldsElement(variableElement, unknownFieldsDeclaredType, setterElement, unknownFieldsType, mixins));
    }

    private ProtobufUnknownFieldsElement createUnknownFieldsElement(VariableElement variableElement, DeclaredType variableType, ExecutableElement setterElement, TypeMirror unknownFieldsType, List<TypeElement> mixins) {
        var defaultValue = getDefaultValue(variableElement, unknownFieldsType, mixins)
                .orElse("new %s()".formatted(variableType)); // For now, could be improved to check if the constructor exists
        return new ProtobufUnknownFieldsElement(variableType, defaultValue, setterElement);
    }

    private ExecutableElement findUnknownFieldsSetterInType(DeclaredType unknownFieldsDeclaredType) {
        return (ExecutableElement) unknownFieldsDeclaredType.asElement()
                .getEnclosedElements()
                .stream()
                .filter(enclosedElement -> enclosedElement.getKind() == ElementKind.METHOD && enclosedElement.getAnnotation(ProtobufUnknownFields.Setter.class) != null)
                .findFirst()
                .orElse(null);
    }

    private ExecutableElement findUnknownFieldsSetterInMixins(VariableElement element, TypeMirror unknownFieldsType, List<TypeElement> mixins) {
        return mixins.stream()
                .map(TypeElement::getEnclosedElements)
                .flatMap(Collection::stream)
                 .filter(enclosedElement -> enclosedElement.getKind() == ElementKind.METHOD && enclosedElement.getAnnotation(ProtobufUnknownFields.Setter.class) != null)
                .map(enclosedElement -> (ExecutableElement) enclosedElement)
                .filter(enclosedMethod -> !enclosedMethod.getParameters().isEmpty() && types.isAssignable(enclosedMethod.getParameters().getFirst().asType(), unknownFieldsType))
                .reduce((first, second) -> {
                    messages.printError("Duplicated protobuf unknown fields setter: only one setter for %s is allowed in the mixins".formatted(unknownFieldsType), element);
                    return first;
                })
                .orElse(null);
    }

    private Optional<ExecutableElement> checkUnknownFieldsSetter(VariableElement variableElement, ExecutableElement setter, boolean fromMixin) {
        if(!setter.getModifiers().contains(Modifier.PUBLIC)) {
            messages.printError("Type error: methods annotated with @ProtobufUnknownFields.Setter must have public visibility", variableElement);
            return Optional.empty();
        }

        if(fromMixin != setter.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Type error: methods annotated with @ProtobufUnknownFields.Setter %s".formatted(fromMixin ? "in a mixin must be static" : "must not be static"), variableElement);
            return Optional.empty();
        }

        if(setter.getParameters().size() != (fromMixin ? 3 : 2)) {
            messages.printError("Type error: methods annotated with @ProtobufUnknownFields.Setter %smust take only %s parameters".formatted(fromMixin ? "in a mixin" : "", fromMixin ? "three" : "two"), variableElement);
            return Optional.empty();
        }

        var firstParameter = setter.getParameters().get(fromMixin ? 1 : 0);
        var keyType = firstParameter.asType();
        if(!types.isAssignable(keyType, Integer.class) && !types.isSameType(keyType, int.class)) {
            messages.printError("Type error: expected int type", setter);
            return Optional.empty();
        }

        var secondParameter = setter.getParameters().get(fromMixin ? 2 : 1);
        var valueType = secondParameter.asType();
        if(!types.isSameType(valueType, Object.class)) {
            messages.printError("Type error: expected Object type", setter);
            return Optional.empty();
        }

        return Optional.of(setter);
    }

    private void processMessageProperty(ProtobufObjectElement messageElement, VariableElement variableElement, ProtobufProperty propertyAnnotation) {
        if(propertyAnnotation.type() == ProtobufType.UNKNOWN) {
            messages.printError("Type error: properties must specify a valid protobuf type", variableElement);
            return;
        }

        if(propertyAnnotation.required() && !checks.isValidRequiredProperty(variableElement)) {
            return;
        }

        if(propertyAnnotation.packed() && !checks.isValidPackedProperty(variableElement, propertyAnnotation)) {
            return;
        }

        var accessor = getAccessor(variableElement, propertyAnnotation)
                .orElse(null);
        if(accessor == null) {
            messages.printError("Missing accessor: a non-private getter/accessor must be declared, or the property must have non-private visibility.", variableElement);
            return;
        }

        var accessorType = getAccessorType(accessor);
        var type = getPropertyType(variableElement, variableElement.asType(), accessorType, propertyAnnotation, null,null);
        if(type.isEmpty()) {
            return;
        }

        var propertyName = variableElement.getSimpleName().toString();
        if(messageElement.isNameDisallowed(propertyName)) {
            messages.printError("Restricted message property name: %s is not allowed as it's marked as reserved".formatted(propertyName), variableElement);
        }

        if(messageElement.isIndexDisallowed(propertyAnnotation.index())) {
            messages.printError("Restricted message property index: %s is not allowed as it's marked as reserved".formatted(propertyAnnotation.index()), variableElement);
        }

        if(propertyAnnotation.ignored()) {
            return;
        }

        var error = messageElement.addProperty(variableElement, accessor, type.get(), propertyAnnotation);
        if(error.isEmpty()) {
            return;
        }

        messages.printError("Duplicated message property: %s and %s with index %s".formatted(variableElement.getSimpleName(), error.get().name(), propertyAnnotation.index()), variableElement);
    }

    private TypeMirror getAccessorType(Element accessor) {
        return switch (accessor) {
            case VariableElement element -> element.asType();
            case ExecutableElement element -> element.getReturnType();
            default -> throw new IllegalStateException("Unexpected value: " + accessor);
        };
    }

    private Optional<String> getCollectionDefaultValue(Element element, TypeMirror collectionType, List<TypeElement> mixins) {
        return types.getDefaultConstructor(collectionType)
                .map(typeElement -> "new %s()".formatted(typeElement.getQualifiedName()))
                .or(() -> getDefaultValue(element, collectionType, mixins));
    }

    private Optional<String> getDefaultValue(Element caller, TypeMirror type, List<TypeElement> mixins) {
        if(type instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement classType) {
            var selfDefaultValue = getDefaultValueFromAnnotation(caller, type, classType);
            if (selfDefaultValue.isPresent()) {
                return selfDefaultValue;
            }
        }

        for(var mixin : mixins) {
            var mixinDefaultValue = getDefaultValueFromAnnotation(caller, type, mixin);
            if (mixinDefaultValue.isPresent()) {
                return mixinDefaultValue;
            }
        }

        return switch (type.getKind()) {
            case INT, CHAR, SHORT, BYTE -> Optional.of("0");
            case BOOLEAN -> Optional.of("false");
            case FLOAT -> Optional.of("0f");
            case DOUBLE -> Optional.of("0d");
            case LONG -> Optional.of("0l");
            default -> Optional.empty();
        };
    }

    private Optional<String> getDefaultValueFromAnnotation(Element caller, TypeMirror type, TypeElement provider) {
        if(provider.getKind() == ElementKind.ENUM) {
            return getEnumDefaultValueFromAnnotation(caller, provider);
        }

        return getObjectDefaultValueFromAnnotation(caller, type, provider);
    }

    private Optional<String> getObjectDefaultValueFromAnnotation(Element caller, TypeMirror type, TypeElement provider) {
        var defaultValueProviderCandidates = new ArrayList<ExecutableElement>();
        for(var element : provider.getEnclosedElements()) {
            if(!(element instanceof ExecutableElement executableElement)) {
                continue;
            }

            var annotation = executableElement.getAnnotation(ProtobufDefaultValue.class);
            if(annotation == null) {
                continue;
            }

            if(types.isAssignable(executableElement.getReturnType(), type)){
                defaultValueProviderCandidates.add(executableElement);
            }
        }

        var bestMatch = defaultValueProviderCandidates.stream().reduce((first, second) -> {
            if(types.isSameType(first.getReturnType(), second.getReturnType())) {
                messages.printError("Duplicated protobuf default value: %s provides a default value that was already defined. Remove the conflicting mixins from the property or the enclosing message.".formatted(second) , caller);
            }

            return types.isAssignable(first.getReturnType(), second.getReturnType()) ? second : first;
        });
        if(bestMatch.isPresent()) {
            var bestMatchOwner = (TypeElement) bestMatch.get().getEnclosingElement();
            return Optional.of(bestMatchOwner.getQualifiedName() + "." + bestMatch.get().getSimpleName() + "()");
        }

        return Optional.empty();
    }

    private Optional<String> getEnumDefaultValueFromAnnotation(Element caller, TypeElement provider) {
        var defaultValueProviderCandidates = new ArrayList<VariableElement>();
        for(var element : provider.getEnclosedElements()) {
            if(!(element instanceof VariableElement variableElement) || variableElement.getKind() != ElementKind.ENUM_CONSTANT) {
                continue;
            }

            var annotation = variableElement.getAnnotation(ProtobufDefaultValue.class);
            if(annotation == null) {
                continue;
            }

            defaultValueProviderCandidates.add(variableElement);
        }

        var bestMatch = defaultValueProviderCandidates.stream().reduce((first, second) -> {
            messages.printError("Duplicated protobuf default value: only one default value is allowed in an enum" , caller);
            return first;
        });
        if(bestMatch.isPresent()) {
            var bestMatchOwner = (TypeElement) bestMatch.get().getEnclosingElement();
            return Optional.of(bestMatchOwner.getQualifiedName() + "." + bestMatch.get().getSimpleName());
        }

        return Optional.empty();
    }

    private Optional<? extends ProtobufPropertyType> getPropertyType(Element element, TypeMirror elementType, TypeMirror accessorType, ProtobufProperty property, TypeMirror rawGroupRepeatedValueType, TypeMirror rawGroupMapValueType) {
        // If the element is a method, we are processing a standalone getter where there is no field
        var mixins = types.getMixins(property);
        if ((rawGroupRepeatedValueType != null && !types.isSameType(rawGroupRepeatedValueType, Object.class)) || types.isAssignable(elementType, Collection.class)) {
            return getConcreteCollectionType(element, property, elementType, mixins, rawGroupRepeatedValueType);
        }

        if(types.isAssignable(elementType, Map.class)) {
            return getConcreteMapType(property, element, elementType, mixins, rawGroupMapValueType);
        }

        if(property.mapKeyType() != ProtobufType.UNKNOWN || property.mapValueType() != ProtobufType.UNKNOWN) {
            if(property.mapKeyType() == ProtobufType.UNKNOWN) {
                messages.printError("Type error: mapKeyType cannot be unknown if mapValueType was specified", element);
            }else if(property.mapValueType() == ProtobufType.UNKNOWN) {
                messages.printError("Type error: mapValueType cannot be unknown if mapKeyType was specified", element);
            }

            return getConcreteMapType(property, element, elementType, mixins, rawGroupMapValueType);
        }

        return getNormalType(element, accessorType, property, elementType, mixins, rawGroupRepeatedValueType != null || rawGroupMapValueType != null);
    }

    private Optional<ProtobufPropertyType.NormalType> getNormalType(Element element, TypeMirror accessorType, ProtobufProperty property, TypeMirror elementType, List<TypeElement> mixins, boolean rawGroupContext) {
        var defaultValue = getDefaultValue(element, elementType, mixins)
                .orElse("null");
        var implementation = new ProtobufPropertyType.NormalType(
                property.type(),
                elementType,
                accessorType,
                defaultValue,
                mixins
        );
        attributeSerializers(element, implementation);
        attributeDeserializers(element, implementation);
        if(!rawGroupContext) {
            createRawGroupSpec(implementation);
        }
        if(!types.isSameType(implementation.serializedType(), implementation.descriptorElementType())) {
            var deserializedDefaultValue = getDefaultValue(element, implementation.deserializedType(), mixins)
                    .orElse("null");
            implementation.setDeserializedDefaultValue(deserializedDefaultValue);
        }

        return Optional.of(implementation);
    }


    private void createRawGroupSpec(ProtobufPropertyType.NormalType implementation) {
        try {
            if(implementation.protobufType() != ProtobufType.GROUP) {
                return;
            }

            var lastSerializer = implementation.rawGroupSerializer()
                    .orElse(null);
            if (lastSerializer == null) {
                return;
            }

            var typeElement = (TypeElement) ((DeclaredType) lastSerializer.parameterType()).asElement();
            var creator = new ProtobufRawGroupSpecGenerator(processingEnv.getFiler());
            var packageName = processingEnv.getElementUtils().getPackageOf(typeElement);
            creator.createClass(typeElement, lastSerializer, packageName);
        }catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Optional<? extends ProtobufPropertyType> getConcreteCollectionType(Element element, ProtobufProperty property, TypeMirror elementType, List<TypeElement> mixins, TypeMirror rawGroupRepeatedValueType) {
        var collectionTypeParameter = rawGroupRepeatedValueType != null && !types.isSameType(rawGroupRepeatedValueType, Object.class) ? rawGroupRepeatedValueType
                : types.getTypeParameter(elementType, types.getType(Collection.class), 0).orElse(null);
        if (collectionTypeParameter == null) {
            messages.printError("Type inference error: cannot determine collection's type parameter", element);
            return Optional.empty();
        }

        var collectionDefaultValue = getCollectionDefaultValue(element, rawGroupRepeatedValueType != null && !types.isSameType(rawGroupRepeatedValueType, Object.class) ? types.getType(Collection.class) : elementType, mixins);
        if(collectionDefaultValue.isEmpty()) {
            messages.printError("Type inference error: cannot determine collection's default value, provide one either in the definition or using a mixin", element);
            return Optional.empty();
        }

        var collectionTypeParameterType = new ProtobufPropertyType.NormalType(
                property.type(),
                collectionTypeParameter,
                collectionTypeParameter,
                null,
                mixins
        );
        attributeSerializers(element, collectionTypeParameterType);
        attributeDeserializers(element, collectionTypeParameterType);
        if(rawGroupRepeatedValueType == null) {
            createRawGroupSpec(collectionTypeParameterType);
        }

        var type = new ProtobufPropertyType.CollectionType(
                elementType,
                collectionTypeParameterType,
                collectionDefaultValue.get(),
                mixins
        );
        return Optional.of(type);
    }

    private Optional<? extends Element> getAccessor(VariableElement fieldElement, ProtobufProperty propertyAnnotation) {
        if(!fieldElement.getModifiers().contains(Modifier.PRIVATE)) {
            return Optional.of(fieldElement);
        }

        var methods = fieldElement.getEnclosingElement()
                .getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof ExecutableElement)
                .map(entry -> (ExecutableElement) entry)
                .filter(element -> !element.getModifiers().contains(Modifier.PRIVATE))
                .toList();
        return methods.stream()
                .filter(entry -> isProtobufGetter(entry, propertyAnnotation))
                .findFirst()
                .or(() -> inferAccessor(fieldElement, methods));
    }

    private Optional<ExecutableElement> inferAccessor(VariableElement fieldElement, List<ExecutableElement> methods) {
        var fieldName = fieldElement.getSimpleName().toString();
        return methods.stream()
                .filter(entry -> isProtobufGetter(entry, fieldName))
                .findFirst();
    }

    private boolean isProtobufGetter(ExecutableElement entry, String fieldName) {
        var methodName = entry.getSimpleName().toString();
        return entry.getParameters().isEmpty() && (methodName.equalsIgnoreCase("get" + fieldName) || methodName.equalsIgnoreCase(fieldName));
    }

    private boolean isProtobufGetter(ExecutableElement entry, ProtobufProperty propertyAnnotation) {
        var annotation = entry.getAnnotation(ProtobufGetter.class);
        return annotation != null && annotation.index() == propertyAnnotation.index();
    }

    // This method is used to get all the metadata about a property with type MAP
    // One might ask why we don't check if the type is a map
    // The reason is that there are cases where a user could want to use a collection or any other object and have a mixin in between to convert them
    private Optional<ProtobufPropertyType.MapType> getConcreteMapType(ProtobufProperty property, Element invoker, TypeMirror elementType, List<TypeElement> mixins, TypeMirror rawGroupMapValueType) {
        if(property.mapKeyType() == ProtobufType.UNKNOWN || property.mapKeyType() == ProtobufType.MAP) { // By default, the mapKeyType is set to map as maps are not supported as key types
            messages.printError("Missing type error: specify the type of the map's key in @%s with mapKeyType".formatted(invoker instanceof ExecutableElement ? "GroupProperty" : "ProtobufProperty"), invoker);
            return Optional.empty();
        }

        if(property.mapValueType() == ProtobufType.UNKNOWN || property.mapValueType() == ProtobufType.MAP) { // By default, the mapValueType is set to map as maps are not supported as value types
            messages.printError("Missing type error: specify the type of the map's value in @%s with mapValueType".formatted(invoker instanceof ExecutableElement ? "GroupProperty" : "ProtobufProperty"), invoker);
            return Optional.empty();
        }

        if(property.mapKeyType() == ProtobufType.MESSAGE || property.mapKeyType() == ProtobufType.ENUM || property.mapKeyType() == ProtobufType.GROUP) { // Objects can't be used as keys in a map following the proto spec
            messages.printError("Type error: protobuf doesn't support messages, enums or groups as keys in a map", invoker);
            return Optional.empty();
        }

        if(property.mapValueType() == ProtobufType.GROUP) { // Objects can't be used as keys in a map following the proto spec
            messages.printError("Type error: protobuf doesn't support groups as values in a map", invoker);
            return Optional.empty();
        }

        if((property.mapValueType() == ProtobufType.MESSAGE || property.mapValueType() == ProtobufType.ENUM) && rawGroupMapValueType != null && types.isSameType(rawGroupMapValueType, Object.class)) { // Objects need metadata when specified by a raw group serializer
            messages.printError("Type error: a property whose type is message or enum in a raw group serializer must specify mapValueImplementation", invoker);
            return Optional.empty();
        }

        // Get the key type of the map that represents the property
        // Example: Map<String, Integer> -> String
        var keyTypeParameter = types.getTypeParameter(elementType, types.getType(Map.class), 0)
                .orElse((property.mapKeyType() != ProtobufType.MESSAGE && property.mapKeyType() != ProtobufType.ENUM) ? types.getType(property.mapKeyType().wrapperType()) : null);
        if (keyTypeParameter == null) {
            messages.printError("Type inference error: cannot determine map's key type", invoker);
            return Optional.empty();
        }

        var keyEntry = new ProtobufPropertyType.NormalType(
                property.mapKeyType(), // Just the proto type of the key
                keyTypeParameter,
                keyTypeParameter,
                null,
                mixins
        );
        attributeSerializers(invoker, keyEntry);
        attributeDeserializers(invoker, keyEntry);
        if(rawGroupMapValueType == null) {
            createRawGroupSpec(keyEntry);
        }

        // Same thing but for the value type
        var valueTypeParameter = rawGroupMapValueType != null && !types.isSameType(rawGroupMapValueType, Object.class) ? rawGroupMapValueType : types.getTypeParameter(elementType, types.getType(Map.class), 1)
                .orElse((property.mapValueType() != ProtobufType.MESSAGE && property.mapValueType() != ProtobufType.ENUM) ? types.getType(property.mapValueType().wrapperType()) : null);
        if (valueTypeParameter == null) {
            messages.printError("Type inference error: cannot determine map's value type", invoker);
            return Optional.empty();
        }

        var valueDefaultValue = getDefaultValue(invoker, valueTypeParameter, mixins)
                .orElse("null");
        var valueEntry = new ProtobufPropertyType.NormalType(
                property.mapValueType(), // Just the protobuf type
                valueTypeParameter,
                valueTypeParameter,
                valueDefaultValue,
                mixins
        );
        attributeSerializers(invoker, valueEntry);
        attributeDeserializers(invoker, valueEntry);
        if(rawGroupMapValueType == null) {
            createRawGroupSpec(valueEntry);
        }

        if(!types.isSameType(valueEntry.serializedType(), valueEntry.descriptorElementType())) {
            var deserializedDefaultValue = getDefaultValue(invoker, valueEntry.deserializedType(), mixins)
                    .orElse("null");
            valueEntry.setDeserializedDefaultValue(deserializedDefaultValue);
        }

        // If the map type is not abstract, create the type as we would with a normal type
        var mapDefaultValue = getCollectionDefaultValue(invoker, elementType, mixins);
        if(mapDefaultValue.isEmpty()) {
            messages.printError("Type inference error: cannot determine map default value", invoker);
            return Optional.empty();
        }

        return Optional.of(new ProtobufPropertyType.MapType(
                elementType,
                keyEntry,
                valueEntry,
                mapDefaultValue.get(),
                mixins
        ));
    }

    private void attributeSerializers(Element invoker, ProtobufPropertyType implementation) {
        attributeSerializers(invoker, implementation.accessorType(), implementation);
    }

    // Add the necessary converters for the provided types using as sources the target type's class and the provided mixins
    private void attributeSerializers(Element invoker, TypeMirror from, ProtobufPropertyType implementation) {
        // If to is a sub type of fromType(ex. Integer and Number) are related and the property isn't a non-protobuf object(i.e. the to type isn't annotated with @ProtobufMessage or @ProtobufEnum), no conversions are necessary
        var to = implementation.protobufType();
        var toWrapped = types.getType(to.wrapperType());
        if (to != ProtobufType.MESSAGE && to != ProtobufType.ENUM && to != ProtobufType.GROUP && types.isAssignable(from, toWrapped)) {
            return;
        }

        // Look for valid serializers and deserializers in the toType and mixins
        var methodPath = serializersGraph.get(from, toWrapped, implementation.mixins());
        if(methodPath.isEmpty()) {
            var toName = getProtobufTypeName(to);
            messages.printError("Missing converter: cannot find a serializer from %s to %s".formatted(from, toName), invoker);
            return;
        }

        for (var element : methodPath) {
            var annotation = element.method().getAnnotation(ProtobufSerializer.class);
            var groupProperties = getGroupProperties(to, element.method(), annotation);
            var serializerElement = new ProtobufSerializerElement(
                    element.method(),
                    from,
                    element.returnType(),
                    groupProperties
            );
            implementation.addConverter(serializerElement);
            from = element.returnType();
        }
    }

    private Map<Integer, ProtobufGroupPropertyElement> getGroupProperties(ProtobufType to, ExecutableElement element, ProtobufSerializer serializer) {
        if (to != ProtobufType.GROUP || serializer.groupProperties().length == 0) {
            return Map.of();
        }

        var cached = rawGroupPropertiesMap.get(element);
        if(cached != null) {
            return cached;
        }

        var results = new HashMap<Integer, ProtobufGroupPropertyElement>();
        rawGroupPropertiesMap.put(element, results);
        for(var groupProperty : serializer.groupProperties()) {
            var implementationType = types.getMirroredType(groupProperty::implementation).asType();
            var actualType = types.isSameType(implementationType, Object.class) ? types.getType(groupProperty.type().wrapperType()) : implementationType;
            var repeatedValueType = types.getMirroredType(groupProperty::repeatedValueImplementation).asType();
            var mapValueType = types.getMirroredType(groupProperty::mapValueImplementation).asType();
            var protobufPropertyType = getPropertyType(element, actualType, actualType, types.getProperty(groupProperty), repeatedValueType, mapValueType);
            if(protobufPropertyType.isEmpty()) {
                messages.printError("Type error: cannot determine type of group property with index %s".formatted(groupProperty.index()), element);
                continue;
            }

            var type = new ProtobufGroupPropertyElement(groupProperty.index(), protobufPropertyType.get(), groupProperty.packed());
            if(results.put(groupProperty.index(), type) != null) {
                messages.printError("Duplicated group property with index %s".formatted(groupProperty.index()), element);
            }
        }

        return results;
    }


    // Add the necessary converters for the provided types using as sources the target type's class and the provided mixins
    private void attributeDeserializers(Element invoker, ProtobufPropertyType implementation) {
        attributeDeserializers(invoker, implementation.descriptorElementType(), implementation);
    }

    private void attributeDeserializers(Element invoker, TypeMirror to, ProtobufPropertyType implementation) {
        // If to is a primitive no conversions are necessary
        // We don't support arrays so no check is necessary
        // If to is a sub type of fromType(ex. Integer and Number) are related and the property isn't a non-protobuf object(i.e. the to type isn't annotated with @ProtobufMessage or @ProtobufEnum), no conversions are necessary
        var from = implementation.protobufType();
        var fromType = types.getType(from.wrapperType());
        if (from != ProtobufType.MESSAGE && from != ProtobufType.ENUM && from != ProtobufType.GROUP && types.isAssignable(to, fromType)) {
            return;
        }

        // Look for valid serializers and deserializers in the toType and mixins
        var methodPath = deserializersGraph.get(fromType, to, implementation.mixins());
        if(methodPath.isEmpty()) {
            var fromName = getProtobufTypeName(from);
            messages.printError("Missing converter: cannot find a deserializer from %s to %s".formatted(fromName, to), invoker);
            return;
        }

        for (var element : methodPath) {
            var annotation = element.method().getAnnotation(ProtobufDeserializer.class);
            var deserializerElement = new ProtobufDeserializerElement(
                    element.method(),
                    fromType,
                    element.returnType(),
                    annotation.builderBehaviour()
            );
            implementation.addConverter(deserializerElement);
            fromType = element.returnType();
        }
    }

    private Object getProtobufTypeName(ProtobufType type) {
        return switch (type) {
            case MESSAGE -> "ProtobufMessage";
            case ENUM -> "ProtobufEnum";
            case GROUP -> "ProtobufGroup";
            default -> {
                var primitiveName = type.serializedType().getSimpleName();
                var wrappedName = type.wrapperType().getSimpleName();
                if(Objects.equals(primitiveName, wrappedName)) {
                    yield "%s(%s)".formatted(type.name(), primitiveName);
                }else {
                    yield "%s(%s/%s)".formatted(type.name(), primitiveName, wrappedName);
                }
            }
        };
    }

    private Optional<ProtobufObjectElement> processEnum(TypeElement enumElement) {
        var messageElement = createEnumElement(enumElement);
        if(messageElement.isEmpty()) {
            return messageElement;
        }

        var constantsCount = processEnumConstants(messageElement.get());
        if(constantsCount != 0) {
            return messageElement;
        }

        messages.printWarning("No constants found", enumElement);
        return messageElement;
    }

    @SuppressWarnings("MappingBeforeCount") // Side effects
    private long processEnumConstants(ProtobufObjectElement messageElement) {
        var enumTree = trees.getTree(messageElement.element());
        return enumTree.getMembers()
                .stream()
                .filter(member -> member instanceof VariableTree)
                .map(member -> (VariableTree) member)
                .peek(variableTree -> processEnumConstant(messageElement, messageElement.element(), variableTree))
                .count();
    }

    private Optional<ProtobufObjectElement> createEnumElement(TypeElement enumElement) {
        var metadata = getEnumMetadata(enumElement);
        if (metadata.isEmpty()) {
            return Optional.of(new ProtobufObjectElement(enumElement, ProtobufEnumMetadata.javaEnum(), null, false));
        }

        if(metadata.get().isUnknown()) {
            return Optional.empty();
        }

        var result = new ProtobufObjectElement(enumElement, metadata.get(), null, false);
        return Optional.of(result);
    }

    private Optional<ProtobufEnumMetadata> getEnumMetadata(TypeElement enumElement) {
        var fields = getEnumFields(enumElement);
        return getConstructors(enumElement)
                .stream()
                .map(constructor -> getEnumMetadata(constructor, fields))
                .flatMap(Optional::stream)
                .reduce((first, second) -> {
                    messages.printError("Duplicated protobuf constructor: an enum should provide only one constructor with a scalar parameter annotated with @ProtobufEnumIndex", second.constructor());
                    return first;
                });
    }

    private Optional<ProtobufEnumMetadata> getEnumMetadata(ExecutableElement constructor, ProtobufEnumFields fields) {
        var constructorTree = trees.getTree(constructor);
        return IntStream.range(0, constructor.getParameters().size())
                .filter(index -> isImplicitEnumConstructor(constructor) || hasProtobufIndexAnnotation(constructor, index))
                .mapToObj(index -> getEnumMetadata(constructor, constructor.getParameters().get(index), index, constructorTree, fields))
                .reduce((first, second) -> {
                    messages.printError("Duplicated protobuf enum index: an enum constructor should provide only one parameter annotated with @ProtobufEnumIndex", second.parameter());
                    return first;
                });
    }

    private boolean isImplicitEnumConstructor(ExecutableElement constructor) {
        return constructor.getParameters().size() == 1
                && types.isSameType(constructor.getParameters().getFirst().asType(), int.class);
    }

    private boolean hasProtobufIndexAnnotation(ExecutableElement constructor, int index) {
        return constructor.getParameters()
                .get(index)
                .getAnnotation(ProtobufEnumIndex.class) != null;
    }

    private ProtobufEnumMetadata getEnumMetadata(ExecutableElement constructor, VariableElement parameter, int index, MethodTree constructorTree, ProtobufEnumFields fields) {
        if(fields.enumIndexField() != null) {
            return new ProtobufEnumMetadata(constructor, fields.enumIndexField(), parameter, index);
        }

        return constructorTree.getBody()
                .getStatements()
                .stream()
                .filter(constructorEntry -> constructorEntry instanceof ExpressionStatementTree)
                .map(constructorEntry -> ((ExpressionStatementTree) constructorEntry).getExpression())
                .filter(constructorEntry -> constructorEntry instanceof AssignmentTree)
                .map(constructorEntry -> (AssignmentTree) constructorEntry)
                .filter(assignmentTree -> isEnumIndexParameterAssignment(assignmentTree, parameter))
                .map(this::getAssignmentExpressionName)
                .flatMap(Optional::stream)
                .map(fields.fields()::get)
                .filter(Objects::nonNull)
                .reduce((first, second) -> {
                    messages.printError("Duplicated assignment: the parameter annotated with @ProtobufEnumIndex must be assigned to a single local field", second);
                    return first;
                })
                .map(fieldElement -> {
                    checkProtobufEnumIndexField(fieldElement);
                    return new ProtobufEnumMetadata(constructor, fieldElement, parameter, index);
                })
                .orElseGet(() -> {
                    messages.printError("Missing or too complex assignment: the parameter annotated with @ProtobufEnumIndex should be assigned to a local field", constructor);
                    messages.printError("If the assignment is too complex for the compiler to evaluate, annotate the local field directly with @ProtobufEnumIndex", constructor);
                    return ProtobufEnumMetadata.unknown();
                });
    }

    private boolean isEnumIndexParameterAssignment(AssignmentTree assignmentTree, VariableElement parameter) {
        return assignmentTree.getExpression() instanceof IdentifierTree identifierTree
                && identifierTree.getName().equals(parameter.getSimpleName());
    }

    private Optional<Name> getAssignmentExpressionName(AssignmentTree assignmentTree) {
        if(assignmentTree.getExpression() instanceof IdentifierTree fieldIdentifier) {
            return Optional.of(fieldIdentifier.getName());
        }else if(assignmentTree.getExpression() instanceof MemberSelectTree memberSelectTree) {
            return Optional.of(memberSelectTree.getIdentifier());
        }else {
            return Optional.empty();
        }
    }

    private ProtobufEnumFields getEnumFields(TypeElement enumElement) {
        var fields = new HashMap<Name, VariableElement>();
        for (var entry : enumElement.getEnclosedElements()) {
            if (!(entry instanceof VariableElement variableElement)) {
                continue;
            }

            if(variableElement.getAnnotation(ProtobufEnumIndex.class) != null) {
                checkProtobufEnumIndexField(variableElement);
                return new ProtobufEnumFields(variableElement, null);
            }

            fields.put(variableElement.getSimpleName(), variableElement);
        }

        return new ProtobufEnumFields(null, fields);
    }

    private void checkProtobufEnumIndexField(VariableElement variableElement) {
        if (!variableElement.getModifiers().contains(Modifier.PRIVATE)) {
            return;
        }

        messages.printError("Weak visibility: the field annotated with @ProtobufEnumIndex must have at least package-private visibility", variableElement);
    }

    private record ProtobufEnumFields(VariableElement enumIndexField, Map<Name, VariableElement> fields) {

    }

    private List<ExecutableElement> getConstructors(TypeElement enumElement) {
        return enumElement.getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof ExecutableElement)
                .map(entry -> (ExecutableElement) entry)
                .filter(entry -> entry.getKind() == ElementKind.CONSTRUCTOR)
                .toList();
    }

    private void processEnumConstant(ProtobufObjectElement messageElement, TypeElement enumElement, VariableTree enumConstantTree) {
        if (!(enumConstantTree.getInitializer() instanceof NewClassTree newClassTree)) {
            return;
        }

        var newClassType = newClassTree.getIdentifier().toString();
        var simpleEnumName = enumElement.getSimpleName().toString();
        if (!newClassType.equals(simpleEnumName) && !newClassType.equals(messageElement.element().getQualifiedName().toString())) {
            return;
        }

        var variableName = enumConstantTree.getName().toString();
        if (messageElement.enumMetadata().orElseThrow().isJavaEnum()) {
            var ordinal = messageElement.constants().size();
            if(messageElement.isIndexDisallowed(ordinal)) {
                messages.printError("Restricted message property index: %s is not allowed as it's marked as reserved".formatted(ordinal), enumElement);
            }

            var error = messageElement.addConstant(ordinal, variableName);
            if (error.isEmpty()) {
                return;
            }

            messages.printError("Duplicated enum constant: %s and %s with index %s".formatted(variableName, error.get(), ordinal), enumElement);
        } else {
            if (newClassTree.getArguments().isEmpty()) {
                messages.printError("%s doesn't specify an index".formatted(variableName), enumElement);
                return;
            }

            var indexArgument = newClassTree.getArguments().get(messageElement.enumMetadata().orElseThrow().parameterIndex());
            if (!(indexArgument instanceof LiteralTree literalTree)) {
                messages.printError("%s's index must be a constant value".formatted(variableName), enumElement);
                return;
            }

            var value = ((Number) literalTree.getValue()).intValue();
            if (value < 0) {
                messages.printError("%s's index must be positive".formatted(variableName), enumElement);
            }

            if(messageElement.isIndexDisallowed(value)) {
                messages.printError("Restricted message property index: %s is not allowed as it's marked as reserved".formatted(value), enumElement);
            }

            var error = messageElement.addConstant(value, variableName);
            if (error.isEmpty()) {
                return;
            }

            messages.printError("Duplicated enum constant: %s and %s with index %s".formatted(variableName, error.get(), value), enumElement);
        }
    }
}
