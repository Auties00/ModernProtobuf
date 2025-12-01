package it.auties.protobuf.serialization;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.generator.*;
import it.auties.protobuf.serialization.graph.ProtobufConverterGraph;
import it.auties.protobuf.serialization.model.*;
import it.auties.protobuf.serialization.support.Checks;
import it.auties.protobuf.serialization.support.Messages;
import it.auties.protobuf.serialization.support.Types;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

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
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation>[] PARSABLE_ANNOTATIONS = new Class[]{
            ProtobufMessage.class,
            ProtobufGroup.class,
            ProtobufEnum.class
    };

    // Useful utility classes to perform checks and print errors/warnings
    private Trees trees;
    private Types types;
    private Messages messages;
    private Checks checks;

    // A graph-like representation of converters to speed up the discovering process
    private ProtobufConverterGraph serializersGraph;
    private ProtobufConverterGraph deserializersGraph;

    // Cached types
    private TypeMirror intType;
    private TypeMirror outputStreamType;
    private TypeMirror serializedGroupType;
    private TypeMirror serializedMessageType;

    // Linked types
    private Set<String> linkedTypes;

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
        this.intType = types.getType(int.class);
        this.outputStreamType = types.getType(ProtobufOutputStream.class);
        this.serializedGroupType = types.getType(ProtobufType.GROUP.serializedType());
        this.serializedMessageType = types.getType(ProtobufType.MESSAGE.serializedType());
        this.linkedTypes = new HashSet<>();
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

    // Flow inspired by Javac
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        runWithTimer("preliminary checks", () -> checks.runChecks(roundEnv));
        var objects = runWithTimer("objects processing", () -> processObjects(roundEnv));
        runWithTimer("objects processing", () -> attributeObjects(objects));
        runWithTimer("code generation", () -> generateCode(objects));
        return true;
    }

    @SuppressWarnings("SameParameterValue")
    private <T> T runWithTimer(String name, Supplier<T> runnable) {
        var start = System.currentTimeMillis();
        messages.printInfo("Running %s...".formatted(name));
        var result = runnable.get();
        messages.printInfo("Finished %s(%dms)".formatted(name, System.currentTimeMillis() - start));
        return result;
    }

    private void runWithTimer(String name, Runnable runnable) {
        var start = System.currentTimeMillis();
        messages.printInfo("Running %s...".formatted(name));
        runnable.run();
        messages.printInfo("Finished %s(%dms)".formatted(name, System.currentTimeMillis() - start));
    }

    private void generateCode(List<ProtobufObjectElement> objects) {
        TypeElement currentElement = null;
        try {
            for(var object : objects) {
                currentElement = object.typeElement();
                var packageName = processingEnv.getElementUtils().getPackageOf(object.typeElement());
                var specVisitor = new ProtobufObjectSpecGenerator(processingEnv.getFiler());
                specVisitor.createClass(object, packageName);

                if (object.type() == ProtobufObjectElement.Type.MESSAGE || object.type() == ProtobufObjectElement.Type.GROUP) {
                    var typeGenerator = new ProtobufBuilderTypeGenerator(processingEnv.getFiler());
                    typeGenerator.createClass(packageName.getQualifiedName().toString(), object);
                    for (var builder : object.builders()) {
                        var methodGenerator = new ProtobufBuilderMethodGenerator(processingEnv.getFiler());
                        methodGenerator.createClass(packageName.getQualifiedName().toString(), object, builder);
                    }
                }
            }
        }catch (IOException throwable) {
            messages.printError("An error occurred while processing protobuf: " + Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getName()), currentElement);
        }
    }

    private void attributeObjects(List<ProtobufObjectElement> objects) {
        for(var object : objects) {
            attributeProperties(object);
        }
    }

    private void attributeProperties(ProtobufObjectElement object) {
        for(var property : object.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> attributeConverter(collectionType.valueType());
                case ProtobufPropertyType.MapType mapType -> {
                    attributeConverter(mapType.keyType());
                    attributeConverter(mapType.valueType());
                }
                case ProtobufPropertyType.NormalType normalType -> attributeConverter(normalType);
            }
        }
    }

    private void attributeConverter(ProtobufPropertyType type) {
        var attributed = new ArrayList<ProtobufConverterElement.Attributed>();
        for(var entry : type.converters()) {
            switch (entry) {
                case ProtobufConverterElement.Attributed attributedElement-> attributed.add(attributedElement);
                case ProtobufConverterElement.Unattributed unattributedElement -> attributed.addAll(attributeConverter(unattributedElement));
            }
        }

        type.clearConverters();

        for(var entry : attributed) {
            type.addConverter(entry);
        }
    }

    private List<ProtobufConverterElement.Attributed> attributeConverter(ProtobufConverterElement.Unattributed unattributedElement) {
        var from = unattributedElement.from();
        var results = new ArrayList<ProtobufConverterElement.Attributed>();
        switch (unattributedElement.type()) {
            case SERIALIZER -> {
                var methodPath = serializersGraph.findPath(from, unattributedElement.to(), unattributedElement.mixins());
                if(methodPath.isEmpty()) {
                    var toName = getProtobufTypeName(unattributedElement.protobufType(), true);
                    messages.printError("Missing converter: cannot find a serializer from %s to %s".formatted(from, toName), unattributedElement.invoker());
                    break;
                }

                for (var element : methodPath) {
                    var warning = element.method().warning();
                    if(!warning.isEmpty()) {
                        messages.printWarning(warning, unattributedElement.invoker());
                    }
                    var serializerElement = new ProtobufConverterElement.Attributed.Serializer(
                            element.method(),
                            from,
                            element.returnType()
                    );
                    results.add(serializerElement);
                    from = element.returnType();
                }
            }
            case DESERIALIZER -> {
                var methodPath = deserializersGraph.findPath(from, unattributedElement.to(), unattributedElement.mixins());
                if(methodPath.isEmpty()) {
                    var fromName = getProtobufTypeName(unattributedElement.protobufType(), false);
                    messages.printError("Missing converter: cannot find a deserializer from %s to %s".formatted(fromName, unattributedElement.to()), unattributedElement.invoker());
                    break;
                }

                for (var element : methodPath) {
                    var warning = element.method().warning();
                    if(!warning.isEmpty()) {
                        messages.printWarning(warning, unattributedElement.invoker());
                    }
                    var deserializerElement = new ProtobufConverterElement.Attributed.Deserializer(
                            element.method(),
                            from,
                            element.returnType()
                    );
                    results.add(deserializerElement);
                    from = element.returnType();
                }
            }
        }
        return results;
    }

    // This is where the actual processing happens
    private List<ProtobufObjectElement> processObjects(RoundEnvironment roundEnv) {
        return Arrays.stream(PARSABLE_ANNOTATIONS)
                .map(roundEnv::getElementsAnnotatedWith)
                .flatMap(Collection::stream)
                .filter(entry -> entry instanceof TypeElement)
                .map(entry -> processElement((TypeElement) entry))
                .flatMap(Set::stream)
                .toList();
    }

    private Set<ProtobufObjectElement> processElement(TypeElement object) {
        if(object.getModifiers().contains(Modifier.ABSTRACT)) {
            return Set.of();
        }

        return switch (object.getKind()) {
            case ENUM -> processEnum(object);
            case RECORD, CLASS -> processObject(object);
            default -> Set.of();
        };
    }

    private Set<ProtobufObjectElement> processObject(TypeElement typeElement) {
        var groupAnnotation = typeElement.getAnnotation(ProtobufGroup.class);
        if(groupAnnotation != null) {
            var deserializer = GetObjectDeserializer(typeElement)
                    .orElse(null);
            var reserved = ProtobufReservedElement.of(groupAnnotation);
            var groupElement =  ProtobufObjectElement.ofGroup(typeElement, deserializer, reserved);
            var results = processObject(groupElement, groupElement.typeElement());
            linkGroup(typeElement.asType());
            if (!hasPropertiesConstructor(groupElement)) {
                messages.printError("Missing protobuf constructor: a protobuf group must provide a constructor that takes as parameters only its properties, following their declaration order, and, if present, its unknown fields wrapper", groupElement.typeElement());
            }
            return results;
        }

        var messageAnnotation = typeElement.getAnnotation(ProtobufMessage.class);
        if(messageAnnotation != null) {
            var deserializer = GetObjectDeserializer(typeElement)
                    .orElse(null);
            var reserved = ProtobufReservedElement.of(messageAnnotation);
            var messageElement = ProtobufObjectElement.ofMessage(typeElement, deserializer, reserved);
            var results = processObject(messageElement, messageElement.typeElement());
            linkMessage(typeElement.asType());
            if (!hasPropertiesConstructor(messageElement)) {
                messages.printError("Missing protobuf constructor: a protobuf message must provide a constructor that takes as parameters only its properties, following their declaration order, and, if present, its unknown fields wrapper", messageElement.typeElement());
            }
            return results;
        }

        // An error will be printed by the preliminary checks
        return Set.of();
    }

    private Optional<ProtobufConverterMethod> GetObjectDeserializer(TypeElement message) {
        ProtobufConverterMethod deserializer = null;
        for(var element : message.getEnclosedElements()) {
            if(!(element instanceof ExecutableElement method)) {
                continue;
            }

            var annotation = method.getAnnotation(ProtobufDeserializer.class);
            if(annotation == null) {
                continue;
            }

            if(deserializer != null) {
                messages.printError("Duplicated protobuf builder delegate: a message or group should provide only one method annotated with @ProtobufDeserializer", method);
                continue;
            }

            deserializer = ProtobufConverterMethod.of(method, types.isParametrized(method), annotation.warning());
        }
        return Optional.ofNullable(deserializer);
    }

    private Set<ProtobufObjectElement> processObject(ProtobufObjectElement messageElement, TypeElement typeElement) {
        // The set of results
        var results = new HashSet<ProtobufObjectElement>();

        // Add the element being processed only if we are not processing a super class
        if(messageElement.typeElement() == typeElement) {
            results.add(messageElement);
        }

        // If this element has a valid super class, process that as well under the same element
        types.getDirectSuperClass(typeElement).ifPresent(superClass -> {
            var superResults = processObject(messageElement, superClass);
            results.addAll(superResults);
        });

        // We could run directly the processing on fields and methods
        // But supporting standalone getters requires either to run fields before methods or to run additional checks later
        var fields = new ArrayList<VariableElement>();
        var methods = new ArrayList<ExecutableElement>();
        for (var entry : typeElement.getEnclosedElements()) {
            switch (entry) {
                case VariableElement variableElement -> fields.add(variableElement);
                case ExecutableElement executableElement -> methods.add(executableElement);
                case null, default -> {}
            }
        }

        // Does the actual processing
        for(var field : fields) {
            results.addAll(processObjectField(messageElement, field));
        }
        for(var method : methods) {
            processObjectMethod(messageElement, method);
        }

        // Return the results
        return results;
    }

    // Does the actual processing on a method in a class/record annotated with @ProtobufMessage/@ProtobufGroup
    // It will try to handle in order:
    // - ProtobufBuilder
    // - ProtobufGetter
    // - ProtobufDeserializer
    // - ProtobufSerializer
    private void processObjectMethod(ProtobufObjectElement messageElement, ExecutableElement executableElement) {
        var builder = executableElement.getAnnotation(ProtobufBuilder.class);
        if(builder != null) {
            messageElement.addBuilder(builder.name(), executableElement.getParameters(), executableElement);
            return;
        }

        var getter = executableElement.getAnnotation(ProtobufAccessor.class);
        if(getter != null) {
            handleMessageGetter(messageElement, executableElement, getter);
            return;
        }

        processObjectSerializer(executableElement);
        processObjectDeserializer(executableElement);
    }

    // Does the actual processing on an input method if it's annotated with @ProtobufDeserializer
    private void processObjectDeserializer(ExecutableElement executableElement) {
        // Checks if the method is annotated with @ProtobufDeserializer
        var deserializer = executableElement.getAnnotation(ProtobufDeserializer.class);
        if (deserializer == null) {
            return;
        }

        // Links the method in the graph, so it can be used to resolve type dependencies later
        var from = executableElement.getParameters().getFirst().asType();
        var to = executableElement.getReturnType();
        ProtobufConverterMethod method = ProtobufConverterMethod.of(executableElement, types.isParametrized(executableElement), deserializer.warning());
        deserializersGraph.link(from, to, method);
    }

    // Does the actual processing on an input method if it's annotated with @ProtobufSerializer
    private void processObjectSerializer(ExecutableElement executableElement) {
        // Checks if the method is annotated with @ProtobufSerializer
        var serializer = executableElement.getAnnotation(ProtobufSerializer.class);
        if(serializer == null) {
            return;
        }

        // Links the method in the graph, so it can be used to resolve type dependencies later
        var from = !executableElement.getParameters().isEmpty() ? executableElement.getParameters().getFirst().asType() : executableElement.getEnclosingElement().asType();
        var to = executableElement.getReturnType();
        var method = ProtobufConverterMethod.of(executableElement, types.isParametrized(executableElement), serializer.warning());
        serializersGraph.link(from, to, method);
    }

    private void handleMessageGetter(ProtobufObjectElement messageElement, ExecutableElement executableElement, ProtobufAccessor getter) {
        if (hasMatchedProperty(messageElement, getter)) {
            return;
        }
        messages.printError("Invalid getter: there is no property with index \"" + getter.index() + "\" in \"" + messageElement.typeElement().getQualifiedName().toString() + "\"", executableElement);
    }

    private boolean hasMatchedProperty(ProtobufObjectElement messageElement, ProtobufAccessor getter) {
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
        return message.typeElement()
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

    private Set<ProtobufObjectElement> processObjectField(ProtobufObjectElement messageElement, VariableElement variableElement) {
        var propertyAnnotation = variableElement.getAnnotation(ProtobufProperty.class);
        if(propertyAnnotation != null) {
            return processMessageProperty(messageElement, variableElement, propertyAnnotation);
        }

        var unknownFieldsAnnotation = variableElement.getAnnotation(ProtobufUnknownFields.class);
        if(unknownFieldsAnnotation != null) {
            processMessageUnknownFields(messageElement, variableElement, unknownFieldsAnnotation);
        }

        return Set.of();
    }

    private void processMessageUnknownFields(ProtobufObjectElement messageElement, VariableElement variableElement, ProtobufUnknownFields unknownFieldsAnnotation) {
        if(messageElement.unknownFieldsElement().isPresent()) {
            messages.printError("Duplicated protobuf unknown fields: a message should provide only one method field annotated with @ProtobufUnknownFields", variableElement);
            return;
        }

        var unknownFields = processUnknownFieldsField(variableElement, unknownFieldsAnnotation);
        if(unknownFields.isEmpty()) {
            return;
        }

        messageElement.setUnknownFieldsElement(unknownFields.get());
        var mixins = types.getMixins(unknownFieldsAnnotation);
        linkMixins(mixins);
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
            return checkUnknownFieldsSetter(setter, false)
                    .map(setterElement -> createUnknownFieldsElement(variableElement, unknownFieldsDeclaredType, setterElement, unknownFieldsType, mixins));
        }

        var setterFromMixin = findUnknownFieldsSetterInMixins(variableElement, unknownFieldsType, mixins);
        if(setterFromMixin == null) {
            messages.printError("Type error: cannot find a @ProtobufUnknownFields.Setter for the provided type", variableElement);
            return Optional.empty();
        }

        return checkUnknownFieldsSetter(setterFromMixin, true)
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

    private Optional<ExecutableElement> checkUnknownFieldsSetter(ExecutableElement setter, boolean fromMixin) {
        if(!setter.getModifiers().contains(Modifier.PUBLIC)) {
            messages.printError("Type error: methods annotated with @ProtobufUnknownFields.Setter must have public visibility", setter);
            return Optional.empty();
        }

        if(fromMixin != setter.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Type error: methods annotated with @ProtobufUnknownFields.Setter %s".formatted(fromMixin ? "in a mixin must be static" : "must not be static"), setter);
            return Optional.empty();
        }

        if(setter.getParameters().size() != (fromMixin ? 3 : 2)) {
            messages.printError("Type error: methods annotated with @ProtobufUnknownFields.Setter %smust take only %s parameters".formatted(fromMixin ? "in a mixin" : "", fromMixin ? "three" : "two"), setter);
            return Optional.empty();
        }

        var error = false;
        var keyType = setter.getParameters()
                .get(fromMixin ? 1 : 0)
                .asType();
        if(!types.isAssignable(keyType, Long.class) && !types.isSameType(keyType, long.class)) {
            messages.printError("Type error: methods annotated with @ProtobufUnknownFields.Setter %smust take as the %s parameter a long".formatted(fromMixin ? "in a mixin" : "", fromMixin ? "second" : "first"), setter);
            error = true;
        }

        var valueType = setter.getParameters()
                .get(fromMixin ? 2 : 1)
                .asType();
        if(!types.isSameType(valueType, Object.class)) {
            messages.printError("Type error: methods annotated with @ProtobufUnknownFields.Setter %smust take as the %s parameter an Object".formatted(fromMixin ? "in a mixin" : "", fromMixin ? "third" : "second"), setter);
            error = true;
        }

        return error ? Optional.empty() : Optional.of(setter);
    }

    private Set<ProtobufObjectElement> processMessageProperty(ProtobufObjectElement messageElement, VariableElement variableElement, ProtobufProperty propertyAnnotation) {
        if (propertyAnnotation.type() == ProtobufType.UNKNOWN) {
            messages.printError("Type error: properties must specify a valid protobuf type", variableElement);
            return Set.of();
        }

        if (propertyAnnotation.required() && !checks.isValidRequiredProperty(variableElement)) {
            return Set.of();
        }

        if (propertyAnnotation.packed() && !checks.isValidPackedProperty(variableElement, propertyAnnotation)) {
            return Set.of();
        }

        var accessor = getAccessor(variableElement, propertyAnnotation)
                .orElse(null);
        if (accessor == null) {
            messages.printError("Missing accessor: a non-private getter/accessor must be declared, or the property must have non-private visibility.", variableElement);
            return Set.of();
        }

        var accessorType = getAccessorType(accessor);
        var variableType = variableElement.asType();
        var type = getPropertyType(variableElement, variableType, accessorType, propertyAnnotation);
        if (type.isEmpty()) {
            return Set.of();
        }

        var propertyName = variableElement.getSimpleName().toString();
        if (!messageElement.isNameAllowed(propertyName)) {
            messages.printError("Restricted message property name: %s is not allowed as it's marked as reserved".formatted(propertyName), variableElement);
        }

        if (!messageElement.isIndexAllowed(propertyAnnotation.index())) {
            messages.printError("Restricted message property index: %s is not allowed as it's marked as reserved".formatted(propertyAnnotation.index()), variableElement);
        }

        if (propertyAnnotation.ignored()) {
            return Set.of();
        }

        var error = messageElement.addProperty(variableElement, accessor, type.get(), propertyAnnotation);
        if (error.isPresent()) {
            messages.printError("Duplicated message property: %s and %s with index %s".formatted(variableElement.getSimpleName(), error.get().name(), propertyAnnotation.index()), variableElement);
            return Set.of();
        }

        var mixins = types.getMixins(propertyAnnotation);
        linkMixins(mixins);
        return linkType(variableType);
    }

    private Set<ProtobufObjectElement> linkType(TypeMirror variableType) {
        if (types.isGroup(variableType)) {
            linkGroup(variableType);
            return Set.of();
        }

        if (types.isMessage(variableType)) {
            linkMessage(variableType);
            return Set.of();
        }

        if (types.isEnum(variableType)) {
            linkEnum(variableType);
            return Set.of();
        }

        if (!(variableType instanceof DeclaredType declaredType) || !(declaredType.asElement() instanceof TypeElement typeElement)) {
            return Set.of();
        }

        for (var typeArgument : declaredType.getTypeArguments()) {
            linkType(typeArgument);
        }

        for (var entry : typeElement.getTypeParameters()) {
            if (entry.asType().getKind() != TypeKind.TYPEVAR) {
                linkType(entry.asType());
            }
        }

        var results = new HashSet<ProtobufObjectElement>();
        var name = typeElement.getQualifiedName().toString();
        if (linkedTypes.add(name)) {
            for (var entry : typeElement.getEnclosedElements()) {
                if (entry instanceof ExecutableElement element) {
                    processObjectSerializer(element);
                    processObjectDeserializer(element);
                }
            }
        }
        return results;
    }

    private void linkEnum(TypeMirror type) {
        var specName = ProtobufMethodGenerator.getSpecFromObject(type);
        var serializer = ProtobufConverterMethod.of(specName, Set.of(Modifier.PUBLIC, Modifier.STATIC), intType, ProtobufObjectSerializationGenerator.METHOD_NAME, type);
        serializersGraph.link(type, intType, serializer);
        var deserializer = ProtobufConverterMethod.of(specName, Set.of(Modifier.PUBLIC, Modifier.STATIC), type, ProtobufDeserializationGenerator.METHOD_NAME, intType);
        deserializersGraph.link(intType, type, deserializer);
    }

    private void linkMessage(TypeMirror type) {
        if (type instanceof DeclaredType declaredType) {
            for (var typeArgument : declaredType.getTypeArguments()) {
                linkType(typeArgument);
            }
        }
        var specName = ProtobufMethodGenerator.getSpecFromObject(type);
        var serializer = ProtobufConverterMethod.of(specName, Set.of(Modifier.PUBLIC, Modifier.STATIC), serializedMessageType, ProtobufSerializationGenerator.METHOD_NAME, type, outputStreamType);
        serializersGraph.link(type, serializedMessageType, serializer);
        var deserializer = ProtobufConverterMethod.of(specName, Set.of(Modifier.PUBLIC, Modifier.STATIC), type, ProtobufDeserializationGenerator.METHOD_NAME, serializedMessageType);
        deserializersGraph.link(serializedMessageType, type, deserializer);
    }

    private void linkGroup(TypeMirror type) {
        if (type instanceof DeclaredType declaredType) {
            for (var typeArgument : declaredType.getTypeArguments()) {
                linkType(typeArgument);
            }
        }
        var specName = ProtobufMethodGenerator.getSpecFromObject(type);
        var serializer = ProtobufConverterMethod.of(specName, Set.of(Modifier.PUBLIC, Modifier.STATIC), serializedGroupType, ProtobufSerializationGenerator.METHOD_NAME, intType, type, outputStreamType);
        serializersGraph.link(type, serializedGroupType, serializer);
        var deserializer = ProtobufConverterMethod.of(specName, Set.of(Modifier.PUBLIC, Modifier.STATIC), type, ProtobufDeserializationGenerator.METHOD_NAME, intType, serializedGroupType);
        deserializersGraph.link(serializedGroupType, type, deserializer);
    }

    private void linkMixins(List<TypeElement> mixins) {
        for (var mixin : mixins) {
            for (var element : mixin.getEnclosedElements()) {
                if (element instanceof ExecutableElement method) {
                    processObjectSerializer(method);
                    processObjectDeserializer(method);
                }
            }
        }
    }

    private TypeMirror getAccessorType(Element accessor) {
        return switch (accessor) {
            case VariableElement element -> element.asType();
            case ExecutableElement element -> element.getReturnType();
            default -> throw new IllegalStateException("Unexpected value: " + accessor);
        };
    }

    private Optional<String> getCollectionDefaultValue(Element invoker, TypeMirror collectionType, List<TypeElement> mixins) {
        return types.getDefaultConstructor(collectionType)
                .map(typeElement -> "new %s()".formatted(typeElement.getQualifiedName()))
                .or(() -> getDefaultValue(invoker, collectionType, mixins));
    }

    private Optional<String> getDefaultValue(Element invoker, TypeMirror type, List<TypeElement> mixins) {
        if(type instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement classType) {
            var selfDefaultValue = getDefaultValueFromAnnotation(invoker, type, classType);
            if (selfDefaultValue.isPresent()) {
                return selfDefaultValue;
            }
        }

        for(var mixin : mixins) {
            var mixinDefaultValue = getDefaultValueFromAnnotation(invoker, type, mixin);
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

    private Optional<String> getDefaultValueFromAnnotation(Element invoker, TypeMirror type, TypeElement provider) {
        var candidates = new ArrayList<Element>();
        for(var element : provider.getEnclosedElements()) {
            var annotation = element.getAnnotation(ProtobufDefaultValue.class);
            if(annotation != null && types.isAssignable(getDefaultValueType(element), type)) {
                candidates.add(element);
            }
        }

        if(candidates.isEmpty()) {
            return Optional.empty();
        }

        var bestMatch = candidates.getFirst();
        var bestMatchType = getDefaultValueType(bestMatch);
        for (var i = 1; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            var candidateType = getDefaultValueType(candidate);
            if (types.isSameType(bestMatchType, candidateType)) {
                messages.printError("Duplicated default value: " + getDefaultValueCaller(candidate) + " provides a default value for type " + bestMatchType + ", which was already defined by " + getDefaultValueType(bestMatch), invoker);
            }

            if(types.isAssignable(bestMatchType, candidateType)) {
                bestMatch = candidate;
                bestMatchType = candidateType;
            }
        }

        return Optional.ofNullable(getDefaultValueCaller(bestMatch));
    }

    private TypeMirror getDefaultValueType(Element bestMatch) {
        return switch(bestMatch) {
            case ExecutableElement executableElement -> executableElement.getReturnType();
            case VariableElement variableElement -> variableElement.asType();
            default -> null;
        };
    }

    private String getDefaultValueCaller(Element bestMatch) {
        var bestMatchOwner = (TypeElement) bestMatch.getEnclosingElement();
        return switch(bestMatch) {
            case ExecutableElement executableElement -> bestMatchOwner.getQualifiedName() + "." + executableElement.getSimpleName() + "()";
            case VariableElement variableElement -> bestMatchOwner.getQualifiedName() + "." + variableElement.getSimpleName();
            default -> null;
        };
    }

    private Optional<? extends ProtobufPropertyType> getPropertyType(Element invoker, TypeMirror elementType, TypeMirror accessorType, ProtobufProperty property) {
        // If the element is a method, we are processing a standalone getter where there is no field
        var mixins = types.getMixins(property);
        if (types.isAssignable(elementType, Collection.class)) {
            return getConcreteCollectionType(invoker, property, elementType, mixins);
        }

        if(types.isAssignable(elementType, Map.class)) {
            return getConcreteMapType(property, invoker, elementType, mixins);
        }

        if(property.mapKeyType() != ProtobufType.UNKNOWN || property.mapValueType() != ProtobufType.UNKNOWN) {
            if(property.mapKeyType() == ProtobufType.UNKNOWN) {
                messages.printError("Type error: mapKeyType cannot be unknown if mapValueType was specified", invoker);
            }else if(property.mapValueType() == ProtobufType.UNKNOWN) {
                messages.printError("Type error: mapValueType cannot be unknown if mapKeyType was specified", invoker);
            }

            return getConcreteMapType(property, invoker, elementType, mixins);
        }

        var defaultValue = getDefaultValue(invoker, elementType, mixins)
                .orElse("null");
        var implementation = new ProtobufPropertyType.NormalType(
                property.type(),
                elementType,
                accessorType,
                defaultValue,
                mixins
        );
        createUnattributedSerializer(invoker, implementation);
        createUnattributedDeserializer(invoker, implementation);
        return Optional.of(implementation);
    }

    private Optional<? extends ProtobufPropertyType> getConcreteCollectionType(Element invoker, ProtobufProperty property, TypeMirror elementType, List<TypeElement> mixins) {
        var collectionTypeParameter = types.getTypeParameter(elementType, types.getType(Collection.class), 0).orElse(null);
        if (collectionTypeParameter == null) {
            messages.printError("Type inference error: cannot determine collection's type parameter", invoker);
            return Optional.empty();
        }

        var collectionDefaultValue = getCollectionDefaultValue(invoker, elementType, mixins);
        if(collectionDefaultValue.isEmpty()) {
            messages.printError("Type inference error: cannot determine collection's default value, provide one either in the definition or using a mixin", invoker);
            return Optional.empty();
        }

        var collectionTypeParameterType = new ProtobufPropertyType.NormalType(
                property.type(),
                collectionTypeParameter,
                collectionTypeParameter,
                null,
                mixins
        );
        createUnattributedSerializer(invoker, collectionTypeParameterType);
        createUnattributedDeserializer(invoker, collectionTypeParameterType);

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
        var annotation = entry.getAnnotation(ProtobufAccessor.class);
        return annotation != null && annotation.index() == propertyAnnotation.index();
    }

    // This method is used to get all the metadata about a property with type MAP
    // One might ask why we don't check if the type is a map
    // The reason is that there are cases where a user could want to use a collection or any other object and have a mixin in between to convert them
    private Optional<ProtobufPropertyType.MapType> getConcreteMapType(ProtobufProperty property, Element invoker, TypeMirror elementType, List<TypeElement> mixins) {
        if(property.mapKeyType() == ProtobufType.UNKNOWN || property.mapKeyType() == ProtobufType.MAP) {
            messages.printError("Missing type error: specify the type of the map's key in @%s with mapKeyType".formatted(invoker instanceof ExecutableElement ? "GroupProperty" : "ProtobufProperty"), invoker);
            return Optional.empty();
        }

        if(property.mapValueType() == ProtobufType.UNKNOWN || property.mapValueType() == ProtobufType.MAP) {
            messages.printError("Missing type error: specify the type of the map's valueType in @%s with mapValueType".formatted(invoker instanceof ExecutableElement ? "GroupProperty" : "ProtobufProperty"), invoker);
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

        // Get the key type of the map that represents the property
        // Example: Map<String, Integer> -> String
        var keyTypeParameter = types.getTypeParameter(elementType, types.getType(Map.class), 0)
                .orElse((property.mapKeyType() != ProtobufType.MESSAGE && property.mapKeyType() != ProtobufType.ENUM) ? types.getType(property.mapKeyType().deserializableType()) : null);
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
        createUnattributedSerializer(invoker, keyEntry);
        createUnattributedDeserializer(invoker, keyEntry);

        // Same thing but for the valueType type
        var valueTypeParameter = types.getTypeParameter(elementType, types.getType(Map.class), 1)
                .orElse(property.mapValueType() != ProtobufType.MESSAGE && property.mapValueType() != ProtobufType.ENUM ? types.getType(property.mapValueType().deserializableType()) : null);
        if (valueTypeParameter == null) {
            messages.printError("Type inference error: cannot determine map's valueType type", invoker);
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
        createUnattributedSerializer(invoker, valueEntry);
        createUnattributedDeserializer(invoker, valueEntry);

        // If the map type is not abstract, create the type as we would with a normal type
        var mapDefaultValue = getCollectionDefaultValue(invoker, elementType, mixins);
        if(mapDefaultValue.isEmpty()) {
            messages.printError("Type inference error: cannot determine map default valueType", invoker);
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

    private void createUnattributedSerializer(Element invoker, ProtobufPropertyType implementation) {
        createUnattributedSerializer(invoker, implementation.accessorType(), implementation);
    }

    // Add the necessary converters for the provided types using as sources the target type's class and the provided mixins
    private void createUnattributedSerializer(Element invoker, TypeMirror from, ProtobufPropertyType implementation) {
        // If to is a sub type of fromType(ex. Integer and Number) are related and the property isn't a non-protobuf object(i.e. the to type isn't annotated with @ProtobufMessage or @ProtobufEnum), no conversions are necessary
        var to = implementation.protobufType();
        var toWrapped = types.getType(to.deserializableType());
        if (to != ProtobufType.MESSAGE && to != ProtobufType.ENUM && to != ProtobufType.GROUP && types.isAssignable(from, toWrapped)) {
            return;
        }

        var unattributed = new ProtobufConverterElement.Unattributed(invoker, from, toWrapped, to, implementation.mixins(), ProtobufConverterElement.Unattributed.Type.SERIALIZER);
        implementation.addConverter(unattributed);
    }


    // Add the necessary converters for the provided types using as sources the target type's class and the provided mixins
    private void createUnattributedDeserializer(Element invoker, ProtobufPropertyType implementation) {
        createUnattributedDeserializer(invoker, implementation.descriptorElementType(), implementation);
    }

    private void createUnattributedDeserializer(Element invoker, TypeMirror to, ProtobufPropertyType implementation) {
        // If to is a primitive no conversions are necessary
        // We don't support arrays so no check is necessary
        // If to is a sub type of fromType(ex. Integer and Number) are related and the property isn't a non-protobuf object(i.e. the to type isn't annotated with @ProtobufMessage or @ProtobufEnum), no conversions are necessary
        var from = implementation.protobufType();
        var fromType = types.getType(from.deserializableType());
        if (from != ProtobufType.MESSAGE && from != ProtobufType.ENUM && from != ProtobufType.GROUP && types.isAssignable(to, fromType)) {
            return;
        }

        var unattributed = new ProtobufConverterElement.Unattributed(invoker, fromType, to, from, implementation.mixins(), ProtobufConverterElement.Unattributed.Type.DESERIALIZER);
        implementation.addConverter(unattributed);
    }

    private Object getProtobufTypeName(ProtobufType type, boolean serializer) {
        return switch (type) {
            case MESSAGE -> "ProtobufMessage";
            case ENUM -> "ProtobufEnum";
            case GROUP -> "ProtobufGroup";
            case STRING -> "ProtobufString";
            default -> "%s(%s)".formatted(type.name(), serializer ? type.serializedType() : type.deserializableType());
        };
    }

    private Set<ProtobufObjectElement> processEnum(TypeElement enumElement) {
        var messageElement = createEnumElement(enumElement);
        if(messageElement.isEmpty()) {
            return Set.of();
        }

        linkEnum(enumElement.asType());

        var constantsCount = processEnumConstants(messageElement.get());
        if(constantsCount == 0) {
            messages.printWarning("No constants found", enumElement);
        }

        return Set.of(messageElement.get());
    }

    private long processEnumConstants(ProtobufObjectElement messageElement) {
        var enumTree = trees.getTree(messageElement.typeElement());
        return enumTree.getMembers()
                .stream()
                .filter(member -> member instanceof VariableTree)
                .map(member -> (VariableTree) member)
                .peek(variableTree -> processEnumConstant(messageElement, messageElement.typeElement(), variableTree))
                .count();
    }

    private Optional<ProtobufObjectElement> createEnumElement(TypeElement enumElement) {
        var enumAnnotation = enumElement.getAnnotation(ProtobufEnum.class);
        if(enumAnnotation == null) {
            // An error will be printed by the preliminary checks
            return Optional.empty();
        }

        var reserved = ProtobufReservedElement.of(enumAnnotation);
        var metadata = getEnumMetadata(enumElement);
        if (metadata.isEmpty()) {
            return Optional.of(ProtobufObjectElement.ofEnum(enumElement, ProtobufEnumMetadata.javaEnum(), reserved));
        }

        if(metadata.get().isUnknown()) {
            return Optional.empty();
        }

        var result = ProtobufObjectElement.ofEnum(enumElement, metadata.get(), reserved);
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
                .getAnnotation(ProtobufEnum.Constant.class) != null;
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

            if(variableElement.getAnnotation(ProtobufEnum.Constant.class) != null) {
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
        if (!newClassType.equals(simpleEnumName) && !newClassType.equals(messageElement.typeElement().getQualifiedName().toString())) {
            return;
        }

        var variableName = enumConstantTree.getName().toString();
        if (messageElement.enumMetadata().orElseThrow().isJavaEnum()) {
            var ordinal = messageElement.constants().size();
            if(!messageElement.isIndexAllowed(ordinal)) {
                messages.printError("Restricted message property index: %s is not allowed as it's marked as reserved".formatted(ordinal), enumElement);
            }

            var error = messageElement.addEnumConstant(ordinal, variableName);
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
                messages.printError("%s's index must be a constant valueType".formatted(variableName), enumElement);
                return;
            }

            var value = ((Number) literalTree.getValue()).intValue();
            if (value < 0) {
                messages.printError("%s's index must be positive".formatted(variableName), enumElement);
            }

            if(!messageElement.isIndexAllowed(value)) {
                messages.printError("Restricted message property index: %s is not allowed as it's marked as reserved".formatted(value), enumElement);
            }

            var error = messageElement.addEnumConstant(value, variableName);
            if (error.isEmpty()) {
                return;
            }

            messages.printError("Duplicated enum constant: %s and %s with index %s".formatted(variableName, error.get(), value), enumElement);
        }
    }
}
