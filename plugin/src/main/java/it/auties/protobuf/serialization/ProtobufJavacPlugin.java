package it.auties.protobuf.serialization;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.generator.clazz.ProtobufBuilderVisitor;
import it.auties.protobuf.serialization.generator.clazz.ProtobufSpecVisitor;
import it.auties.protobuf.serialization.object.ProtobufEnumMetadata;
import it.auties.protobuf.serialization.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.Converters;
import it.auties.protobuf.serialization.support.Messages;
import it.auties.protobuf.serialization.support.PreliminaryChecks;
import it.auties.protobuf.serialization.support.Types;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.util.*;
import java.util.function.Supplier;
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
    private Trees trees;
    private Types types;
    private Messages messages;
    private Converters converters;
    private PreliminaryChecks preliminaryChecks;

    // Called when the annotation processor gets initialized
    @Override
    public synchronized void init(ProcessingEnvironment wrapperProcessingEnv) {
        var unwrappedProcessingEnv = unwrapProcessingEnv(wrapperProcessingEnv);
        super.init(unwrappedProcessingEnv);
        this.trees = Trees.instance(processingEnv);
        this.types = new Types(processingEnv);
        this.messages = new Messages(processingEnv);
        this.converters = new Converters(types);
        this.preliminaryChecks = new PreliminaryChecks(messages);
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
        checkAnnotations(roundEnv);
        processObjects(roundEnv);
        return true;
    }

    // Preliminary checks on the annotations' scope
    private void checkAnnotations(RoundEnvironment roundEnv) {
        preliminaryChecks.checkEnclosing(
                roundEnv,
                ProtobufProperty.class,
                "Illegal enclosing class: a field annotated with @ProtobufProperty should be enclosed by a class or record annotated with @ProtobufMessage",
                ProtobufMessage.class
        );

        preliminaryChecks.checkEnclosing(
                roundEnv,
                ProtobufGetter.class,
                "Illegal enclosing class: a method annotated with @ProtobufGetter should be enclosed by a class or record annotated with @ProtobufMessage",
                ProtobufMessage.class
        );

        preliminaryChecks.checkEnclosing(
                roundEnv,
                ProtobufEnumIndex.class,
                "Illegal enclosing class: a field or parameter annotated with @ProtobufEnumIndex should be enclosed by an enum annotated with @ProtobufEnum",
                ProtobufEnum.class
        );

        preliminaryChecks.checkEnclosing(
                roundEnv,
                ProtobufDefaultValue.class,
                "Illegal enclosing class: a method or enum constant annotated with @ProtobufDefaultValue should be enclosed by a class annotated with @ProtobufMessage, @ProtobufEnum or @ProtobufMixin",
                ProtobufMessage.class,
                ProtobufEnum.class,
                ProtobufMixin.class
        );

        var serializers = roundEnv.getElementsAnnotatedWith(ProtobufSerializer.class);
        preliminaryChecks.checkSerializers(serializers);

        var deserializers = roundEnv.getElementsAnnotatedWith(ProtobufDeserializer.class);
        preliminaryChecks.checkDeserializers(deserializers);

        var builders = roundEnv.getElementsAnnotatedWith(ProtobufBuilder.class);
        preliminaryChecks.checkBuilders(builders);

        var defaultValues = roundEnv.getElementsAnnotatedWith(ProtobufDefaultValue.class);
        preliminaryChecks.checkDefaultValues(defaultValues);
    }

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
                var specVisitor = new ProtobufSpecVisitor(processingEnv);
                specVisitor.createClass(result.get(), packageName);
                if(result.get().isEnum()){
                    continue;
                }

                var buildVisitor = new ProtobufBuilderVisitor(processingEnv.getFiler());
                buildVisitor.createClass(result.get(), null, packageName);
                for (var builder : result.get().builders()) {
                    buildVisitor.createClass(result.get(), builder, packageName);
                }
            }
        }catch (Throwable throwable) {
            messages.printError("An error occurred while processing protobuf: " + Objects.requireNonNullElse(throwable.getMessage(), throwable.getClass().getName()), currentElement);
        }
    }

    private List<TypeElement> getProtobufObjects(RoundEnvironment roundEnv) {
        return Stream.of(roundEnv.getElementsAnnotatedWith(ProtobufMessage.class), roundEnv.getElementsAnnotatedWith(ProtobufEnum.class))
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
        var messageElement = new ProtobufObjectElement(message, null, builderDelegate.orElse(null));
        processMessage(messageElement, messageElement.element());
        if(messageElement.properties().isEmpty()) {
            messages.printWarning("No properties found", message);
            return Optional.of(messageElement);
        }

        if (!hasPropertiesConstructor(messageElement)) {
            messages.printError("Missing protobuf constructor: a protobuf message must provide a constructor that takes only its properties, following their declaration order, as parameters", message);
            return Optional.empty();
        }

        return Optional.of(messageElement);
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


    private void processMessage(ProtobufObjectElement messageElement, TypeElement typeElement) {
        getSuperClass(typeElement)
                .ifPresent(superClass -> processMessage(messageElement, superClass));
        for (var entry : typeElement.getEnclosedElements()) {
            switch (entry) {
                case VariableElement variableElement -> processProperty(messageElement, variableElement);
                case ExecutableElement executableElement -> processMethod(messageElement, executableElement);
                case null, default -> {}
            }
        }
    }

    private Optional<TypeElement> getSuperClass(TypeElement typeElement) {
        var superClass = typeElement.getSuperclass();
        if(superClass == null || superClass.getKind() == TypeKind.NONE) {
            return Optional.empty();
        }

        var superClassName = superClass.toString();
        var superClassTypeParametersStartIndex = superClassName.indexOf("<");
        var rawName = superClassTypeParametersStartIndex <= 0 ? superClassName : superClassName.substring(0, superClassTypeParametersStartIndex);
        var superClassElement = processingEnv.getElementUtils().getTypeElement(rawName);
        return Optional.ofNullable(superClassElement);
    }

    private void processMethod(ProtobufObjectElement messageElement, ExecutableElement executableElement) {
        var builder = executableElement.getAnnotation(ProtobufBuilder.class);
        if(builder != null) {
            messageElement.addBuilder(builder.className(), executableElement.getParameters(), executableElement);
        }
    }

    private boolean hasPropertiesConstructor(ProtobufObjectElement message) {
        return message.element()
                .getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof ExecutableElement)
                .map(entry -> (ExecutableElement) entry)
                .filter(entry -> entry.getKind() == ElementKind.CONSTRUCTOR)
                .anyMatch(constructor -> {
                    var constructorParameters = constructor.getParameters();
                    if(message.properties().size() != constructorParameters.size()) {
                        return false;
                    }

                    var propertiesIterator = message.properties().iterator();
                    var constructorParametersIterator = constructorParameters.iterator();
                    while (propertiesIterator.hasNext() && constructorParametersIterator.hasNext()) {
                        var property = propertiesIterator.next();
                        var constructorParameter = constructorParametersIterator.next();
                        if(!types.isSubType(property.type().descriptorElementType(), constructorParameter.asType())) {
                            return false;
                        }
                    }

                    return true;
                });
    }

    private void processProperty(ProtobufObjectElement messageElement, VariableElement variableElement) {
        var propertyAnnotation = variableElement.getAnnotation(ProtobufProperty.class);
        if(propertyAnnotation == null) {
            return;
        }

        if(propertyAnnotation.required() && !isValidRequiredProperty(variableElement)) {
            return;
        }

        if(propertyAnnotation.packed() && !isValidPackedProperty(variableElement, propertyAnnotation)) {
           return;
        }

        var accessor = getAccessor(variableElement, propertyAnnotation)
                .orElse(null);
        if(accessor == null) {
            messages.printError("Missing accessor: a non-private getter/accessor must be declared, or the property must have non-private visibility.", variableElement);
            return;
        }

        var accessorType = getAccessorType(accessor);
        var type = getImplementationType(variableElement, accessorType, propertyAnnotation);
        if(type.isEmpty()) {
            return;
        }

        var error = messageElement.addProperty(variableElement, accessor, type.get(), propertyAnnotation);
        if(error.isEmpty()) {
            return;
        }

        messages.printError("Duplicated message property: %s and %s with index %s".formatted(variableElement.getSimpleName(), error.get().name(), propertyAnnotation.index()), variableElement);
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
        
        var defaultValueProviderCandidates = new ArrayList<ExecutableElement>();
        for(var element : provider.getEnclosedElements()) {
            if(!(element instanceof ExecutableElement executableElement)) {
                continue;
            }
            
            var annotation = executableElement.getAnnotation(ProtobufDefaultValue.class);
            if(annotation == null) {
                continue;
            }
            
            if(types.isSubType(type, executableElement.getReturnType())){
                defaultValueProviderCandidates.add(executableElement);
            }
        }
        
        var bestMatch = defaultValueProviderCandidates.stream().reduce((first, second) -> {
            if(types.isSameType(first.getReturnType(), second.getReturnType())) {
                messages.printError("Duplicated protobuf default value: %s provides a default value that was already defined. Remove the conflicting mixins from the property or the enclosing message.".formatted(second) , caller);
            }

            return types.isSubType(first.getReturnType(), second.getReturnType()) ? first : second;
        });
        if(bestMatch.isPresent()) {
            var bestMatchOwner = (TypeElement) bestMatch.get().getEnclosingElement();
            return Optional.of(bestMatchOwner.getQualifiedName() + "." + bestMatch.get().getSimpleName() + "()");
        }

        return Optional.empty();
    }

    private TypeMirror getAccessorType(Element accessor) {
        return switch (accessor) {
            case VariableElement element -> element.asType();
            case ExecutableElement element -> element.getReturnType();
            default -> throw new IllegalStateException("Unexpected value: " + accessor);
        };
    }

    private List<TypeElement> getMixins(ProtobufProperty property) {
        try {
            return Arrays.stream(property.mixins())
                    .map(mixin -> processingEnv.getElementUtils().getTypeElement(mixin.getName()))
                    .filter(entry -> entry instanceof DeclaredType)
                    .map(entry -> (TypeElement) ((DeclaredType) entry).asElement())
                    .toList();
        }catch (MirroredTypesException exception) {
            return exception.getTypeMirrors()
                    .stream()
                    .filter(entry -> entry instanceof DeclaredType)
                    .map(entry -> (TypeElement) ((DeclaredType) entry).asElement())
                    .toList();
        }
    }

    private Optional<ProtobufPropertyType> getImplementationType(Element element, TypeMirror elementType, ProtobufProperty property) {
        var override = getMirroredOverride(property::overrideType);
        var repeatedOverride = getMirroredOverride(property::overrideRepeatedType);
        var mixins = getMixins(property);
        if (types.isSubType(elementType, Collection.class) || repeatedOverride.isPresent()) {
            var collectionTypeParameter = getTypeParameter(elementType, types.getType(Collection.class), 0);
            if (override.isEmpty() && collectionTypeParameter.isEmpty()) {
                messages.printError("Type inference error: cannot determine collection's type parameter. Specify the type explicitly in @ProtobufProperty with overrideType", element);
                return Optional.empty();
            }

            var collectionType = getMirroredOverride(property::overrideRepeatedType)
                    .orElse(elementType);
            var collectionDefaultValue = getCollectionDefaultValue(element, collectionType, mixins);
            if(collectionDefaultValue == null) {
                messages.printError("Type inference error: cannot determine collection's type. Specify the type explicitly in @ProtobufProperty with overrideRepeatedType", element);
                return Optional.empty();
            }

            var implementationType = override.orElseGet(collectionTypeParameter::get);
            var collectionTypeParameterDefaultValue = getDefaultValue(element, implementationType, mixins)
                    .orElse("null");
            var collectionTypeParameterType = new ProtobufPropertyType.NormalType(
                    property.type(),
                    collectionTypeParameter.orElseGet(override::get),
                    implementationType,
                    collectionTypeParameterDefaultValue,
                    types.isMessage(implementationType),
                    types.isEnum(implementationType)
            );
            var type = new ProtobufPropertyType.CollectionType(
                    elementType,
                    collectionTypeParameterType,
                    collectionDefaultValue
            );
            attributeConverters(
                    element,
                    property.type(),
                    implementationType,
                    collectionTypeParameterType,
                    mixins
            );
            return Optional.of(type);
        }

        var overrideMapType = getMirroredOverride(property::overrideMapType);
        var overrideMapKeyType = getMirroredOverride(property::overrideMapKeyType);
        var overrideMapValueType = getMirroredOverride(property::overrideMapValueType);
        if(types.isSubType(elementType, Map.class) || overrideMapType.isPresent() || property.mapKeyType() != ProtobufType.MAP || overrideMapKeyType.isPresent() || property.mapValueType() != ProtobufType.MAP || overrideMapValueType.isPresent()) {
            var concreteMapType = getConcreteMapType(
                    property,
                    element,
                    elementType,
                    mixins
            );
            if (concreteMapType.isEmpty()) {
                return Optional.empty();
            }

            attributeConverters(
                    element,
                    concreteMapType.get().keyType().protobufType(),
                    concreteMapType.get().keyType().descriptorElementType(),
                    concreteMapType.get().keyType(), mixins
            );
            attributeConverters(
                    element,
                    concreteMapType.get().valueType().protobufType(),
                    concreteMapType.get().valueType().descriptorElementType(),
                    concreteMapType.get().valueType(),
                    mixins
            );
            return Optional.of(concreteMapType.get());
        }

        var implementationType = override.orElse(elementType);
        var implementation = new ProtobufPropertyType.NormalType(
                property.type(),
                elementType,
                implementationType,
                getDefaultValue(element, implementationType, mixins).orElse("null"),
                types.isMessage(implementationType),
                types.isEnum(implementationType)
        );
        attributeConverters(element, property.type(), elementType, implementation, mixins);
        return Optional.of(implementation);
    }

    private String getCollectionDefaultValue(Element element, TypeMirror collectionType, List<TypeElement> mixins) {
        if (types.erase(collectionType) instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement
                && !typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return "new %s()".formatted(typeElement.getQualifiedName());
        }

        return getDefaultValue(element, collectionType, mixins).orElse(null);
    }

    private Optional<TypeMirror> getMirroredOverride(Supplier<Class<?>> supplier) {
        try {
            var implementation = supplier.get();
            var type = processingEnv.getElementUtils().getTypeElement(implementation.getName());
            if(type == null || types.isSameType(type.asType(), Object.class)) {
                return Optional.empty();
            }

            return Optional.of(type.asType());
        }catch (MirroredTypeException exception) {
            if (!(exception.getTypeMirror() instanceof DeclaredType declaredType)) {
                return Optional.empty();
            }

            if(types.isSameType(declaredType, Object.class)) {
                return Optional.empty();
            }

            return Optional.of(declaredType);
        }
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
    private Optional<ProtobufPropertyType.MapType> getConcreteMapType(ProtobufProperty property, Element element, TypeMirror elementType, List<TypeElement> mixins) {
        var error = false; // Fail later
        if(property.mapKeyType() == ProtobufType.MAP) { // By default, the mapKeyType is set to map as maps are not supported as key types
            messages.printError("Missing type error: specify the type of the map's key in @ProtobufProperty with keyType", element);
            error = true;
        }

        if(property.mapValueType() == ProtobufType.MAP) { // By default, the mapValueType is set to map as maps are not supported as value types
            messages.printError("Missing type error: specify the type of the map's value in @ProtobufProperty with valueType", element);
            error = true;
        }

        if(property.mapKeyType() == ProtobufType.OBJECT) { // Objects can't be used as keys in a map following the proto spec
            messages.printError("Type error: protobuf doesn't support objects as keys in a map", element);
            error = true;
        }

        // Get the overrideMapKeyType as a TypeMirror
        var keyTypeOverride = getMirroredOverride(property::overrideMapKeyType);
        // Get the key type of the map that represents the property
        // Example: Map<String, Integer> -> String
        var keyTypeParameter = getTypeParameter(elementType, types.getType(Map.class), 0);
        if (keyTypeOverride.isEmpty() && keyTypeParameter.isEmpty()) {
            messages.printError("Type inference error: cannot determine map's key type. Specify the type explicitly in @ProtobufProperty with overrideKeyType", element);
            error = true;
        }

        // Build the protobuf type model for the key
        var keyDescriptorType = keyTypeParameter.or(() -> keyTypeOverride)
                .orElseThrow();
        var keyImplementationType = keyTypeOverride.or(() -> keyTypeParameter)
                .orElseThrow();
        var keyDefaultValue = getDefaultValue(element, keyImplementationType, mixins)
                .orElse("null");
        var keyEntry = new ProtobufPropertyType.NormalType(
                property.mapKeyType(), // Just the proto type of the key
                keyDescriptorType, // Follow hierarchy explained in ProtobufPropertyType
                keyImplementationType, // Follow hierarchy explained in ProtobufPropertyType
                keyDefaultValue,
                false,  // Objects can't be keys
                false // Objects can't be keys, so enums can't as well
        );

        // Same thing but for the value type
        var valueTypeOverride = getMirroredOverride(property::overrideMapValueType);
        var valueTypeParameter = getTypeParameter(elementType, types.getType(Map.class), 1);
        if (valueTypeOverride.isEmpty() && valueTypeParameter.isEmpty()) {
            messages.printError("Type inference error: cannot determine map's value type. Specify the type explicitly in @ProtobufProperty with overrideValueType", element);
            error = true;
        }

        var valueDescriptorType = valueTypeParameter.or(() -> valueTypeOverride)
                .orElseThrow();
        var valueImplementationType = valueTypeOverride.or(() -> valueTypeParameter)
                .orElseThrow();
        var valueDefaultValue = getDefaultValue(element, valueImplementationType, mixins)
                .orElse("null");
        var valueEntry = new ProtobufPropertyType.NormalType(
                property.mapValueType(), // Just the protobuf type
                valueDescriptorType,  // Follow hierarchy explained in ProtobufPropertyType
                valueImplementationType,  // Follow hierarchy explained in ProtobufPropertyType
                valueDefaultValue,
                types.isMessage(valueImplementationType), // Values can be object so we have to check
                types.isEnum(valueImplementationType) // Values can be enums so we have to check
        );

        // If the type isn't a declared type(ex. a primitive) or if the type is not a map(ex. a collection)
        // Use the overrideMapType or an HashMap
        // This is useful for example if the user is using a mixin to convert between a Map and a Collection
        if(!(elementType instanceof DeclaredType declaredFieldType) || !types.isSubType(declaredFieldType, Map.class)) {
            if(error) {
                return Optional.empty();
            }

            return Optional.of(new ProtobufPropertyType.MapType(
                    elementType,
                    keyEntry,
                    valueEntry,
                    "new java.util.HashMap()"
            ));
        }

        // If the map type is not abstract, create the type as we would with a normal type
        var mapType = getMirroredOverride(property::overrideMapType)
                .orElse(elementType);
        var mapDefaultValue = getDefaultValue(element, mapType, mixins)
                .orElseThrow();
        if(mapDefaultValue.isEmpty()) {
            messages.printError("Type inference error: cannot determine concrete map type. Use a concrete type for the property's field(for example HashMap instead of Map) or specify the type explicitly in @ProtobufProperty with overrideMapType", element);
            return Optional.empty();
        }

        return Optional.of(new ProtobufPropertyType.MapType(
                mapType,  // Follow hierarchy explained in ProtobufPropertyType
                keyEntry,
                valueEntry,
                mapDefaultValue
        ));
    }

    // Add the necessary converters for the provided types using as sources the target type's class and the provided mixins
    private void attributeConverters(Element invoker, ProtobufType from, TypeMirror to, ProtobufPropertyType implementation, List<TypeElement> mixins) {
        // If to is a primitive no conversions are necessary
        // We don't support arrays so no check is necessary
        if(!(to instanceof DeclaredType toDeclaredType)) {
            return;
        }

        // If to is a sub type of fromType(ex. Integer and Number) are related and the property isn't a non-protobuf object(i.e. the to type isn't annotated with @ProtobufMessage or @ProtobufEnum), no conversions are necessary
        var fromType = types.getType(from.wrappedType());
        if (types.isSubType(to, fromType)
                && (from != ProtobufType.OBJECT || types.isMessage(toDeclaredType) || types.isEnum(toDeclaredType))) {
            return;
        }

        // Look for valid serializers and deserializers in the toType and mixins
        var serializers = new ArrayList<ProtobufSerializerElement>();
        var deserializers = new ArrayList<ProtobufDeserializerElement>();
        var candidates = new ArrayList<>(mixins);
        candidates.add((TypeElement) toDeclaredType.asElement());
        for(var candidate : candidates) {
            for(var entry : candidate.getEnclosedElements()) {
                if (!(entry instanceof ExecutableElement element)) {
                    continue;
                }

                if (converters.isSerializer(element, to, fromType)) {
                    if (from != ProtobufType.OBJECT || !isParametrized(element)) {
                        var serializer = new ProtobufSerializerElement(element, element.getReturnType());
                        serializers.add(serializer);
                        continue;
                    }

                    var serializerInputType = element.getParameters()
                            .getFirst()
                            .asType();
                    var inferredType = getTypeParameter(to, serializerInputType, 0);
                    if(inferredType.isEmpty()) {
                        messages.printError("Type inference error: cannot determine serializer's type parameter", element); // There is no solution here, if the type cannot be inferred
                        continue;
                    }

                    var serializer = new ProtobufSerializerElement(element, inferredType.get());
                    serializers.add(serializer);
                    continue;
                }

                converters.getDeserializerBuilderBehaviour(element, to, fromType).ifPresent(builderBehaviour -> {
                    if (from != ProtobufType.OBJECT || !isParametrized(element)) {
                        var deserializer = new ProtobufDeserializerElement(element, fromType, builderBehaviour);
                        deserializers.add(deserializer);
                        return;
                    }

                    var inferredType = getTypeParameter(to, element.getReturnType(), 0);
                    if(inferredType.isEmpty()) {
                        messages.printError("Type inference error: cannot determine deserializer's type parameter", element); // There is no solution here, if the type cannot be inferred
                        return;
                    }

                    var deserializer = new ProtobufDeserializerElement(element, inferredType.get(), builderBehaviour);
                    deserializers.add(deserializer);
                });
            }
        }

        // Prevent repeated attribution of the serializer/deserializer type if it's not a protobuf message/enum
        var recursiveNonProtoAttribution = false;

        // Add the best serializer or error out
        if (serializers.isEmpty()) {
            messages.printError("Missing converter: cannot find a serializer for %s".formatted(to), invoker);
        } else {
            var bestSerializer = serializers.stream()
                    .reduce((first, second) -> {
                        var firstType = first.delegate().getReturnType();
                        var secondType = second.delegate().getReturnType();
                        if(types.isSameType(firstType, secondType)) {
                            messages.printError("Duplicated protobuf serializer for %s".formatted(firstType) , second.delegate());
                        }

                        return types.isSubType(firstType, secondType) ? first : second;
                    })
                    .orElseThrow();
            implementation.addNullableConverter(bestSerializer);
            recursiveNonProtoAttribution = !types.isMessage(bestSerializer.returnType()) && !types.isEnum(bestSerializer.returnType());
            if(recursiveNonProtoAttribution) {
                attributeConverters(invoker, from, bestSerializer.returnType(), implementation, mixins);
            }
        }

        // Add the best deserializer or error out
        if (deserializers.isEmpty()) {
            messages.printError("Missing converter: cannot find a deserializer for %s".formatted(fromType), invoker);
        } else {
            var bestDeserializer = deserializers.stream()
                    .reduce((first, second) -> {
                        var firstType = first.delegate().getParameters().getFirst().asType();
                        var secondType = second.delegate().getParameters().getFirst().asType();
                        if(types.isSameType(firstType, secondType)) {
                            messages.printError("Duplicated protobuf deserializer for %s".formatted(firstType) , second.delegate());
                        }

                        return types.isSubType(firstType, secondType) ? first : second;
                    })
                    .orElseThrow();
            implementation.addNullableConverter(bestDeserializer);
            if(!recursiveNonProtoAttribution && !types.isMessage(bestDeserializer.parameterType()) && !types.isEnum(bestDeserializer.parameterType())) {
                attributeConverters(invoker, from, bestDeserializer.parameterType(), implementation, mixins);
            }
        }
    }

    // Checks if a method takes any number of parameters whose type is generic, ex. T, or whose definition depends on a generic type, ex. Map<String, T>
    private boolean isParametrized(ExecutableElement element) {
        return (!element.getTypeParameters().isEmpty() || (element.getEnclosingElement() instanceof TypeElement typeElement && !typeElement.getTypeParameters().isEmpty()))
                && element.getParameters().stream().anyMatch(this::isParametrized);
    }

    private boolean isParametrized(VariableElement parameter) {
        return parameter.asType().getKind() == TypeKind.TYPEVAR ||
                parameter.asType() instanceof DeclaredType declaredType
                        && declaredType.asElement() instanceof TypeElement typeElement
                        && isParametrized(typeElement);
    }

    private boolean isParametrized(Element element) {
        return element instanceof TypeElement typeElement && typeElement.getTypeParameters()
                .stream()
                .anyMatch(entry -> entry.asType().getKind() == TypeKind.TYPEVAR || isParametrized(entry));
    }

    private Optional<TypeMirror> getTypeParameter(TypeMirror mirror, TypeMirror targetType, int index) {
        if(!(mirror instanceof DeclaredType declaredType)) {
            return Optional.empty();
        }

        if (types.isSameType(mirror, targetType)) {
            var collectionTypeArgument = declaredType.getTypeArguments().get(index);
            return getConcreteTypeParameter(collectionTypeArgument, declaredType, index);
        }

        var typeElement = (TypeElement) declaredType.asElement();
        return typeElement.getInterfaces()
                .stream()
                .filter(implemented -> implemented instanceof DeclaredType)
                .map(implemented -> (DeclaredType) implemented)
                .map(implemented -> getTypeParameterByImplement(declaredType, implemented, targetType, index))
                .flatMap(Optional::stream)
                .findFirst()
                .or(() -> getTypeParameterBySuperClass(declaredType, typeElement, targetType, index));
    }

    private Optional<TypeMirror> getTypeParameterByImplement(DeclaredType declaredType, DeclaredType implemented, TypeMirror targetType, int index) {
        if (types.isSameType(implemented, targetType)) {
            var collectionTypeArgument = implemented.getTypeArguments().get(index);
            return getConcreteTypeParameter(collectionTypeArgument, declaredType, index);
        }

        return getTypeParameter(implemented, targetType, index)
                .flatMap(result -> getConcreteTypeParameter(result, declaredType, index));
    }

    private Optional<TypeMirror> getTypeParameterBySuperClass(DeclaredType declaredType, TypeElement typeElement, TypeMirror targetType, int index) {
        if (!(typeElement.getSuperclass() instanceof DeclaredType superDeclaredType)) {
            return Optional.empty();
        }

        return getTypeParameter(superDeclaredType, targetType, index)
                .flatMap(result -> getConcreteTypeParameter(result, superDeclaredType, index))
                .flatMap(result -> getConcreteTypeParameter(result, declaredType, index));
    }

    private Optional<TypeMirror> getConcreteTypeParameter(TypeMirror argumentMirror, DeclaredType previousType, int index) {
        return switch (argumentMirror) {
            case DeclaredType declaredTypeArgument -> Optional.of(declaredTypeArgument);
            case ArrayType arrayType -> Optional.of(arrayType);
            case TypeVariable typeVariableArgument -> getConcreteTypeFromTypeVariable(typeVariableArgument, previousType, index);
            case null, default -> Optional.empty();
        };
    }

    private Optional<TypeMirror> getConcreteTypeFromTypeVariable(TypeVariable typeVariableArgument, DeclaredType previousType, int index) {
        var currentTypeVarName = typeVariableArgument.asElement().getSimpleName();
        var previousTypeArguments = previousType.getTypeArguments();
        var previousElement = (TypeElement) previousType.asElement();
        var previousTypeParameters = previousElement.getTypeParameters();
        for(;index < previousTypeParameters.size() && index < previousTypeArguments.size(); index++) {
            if(previousTypeParameters.get(index).getSimpleName().equals(currentTypeVarName)){
                return Optional.of(previousTypeArguments.get(index));
            }
        }
        return Optional.empty();
    }

    private boolean isValidRequiredProperty(VariableElement variableElement) {
        if(variableElement.asType().getKind().isPrimitive()) {
            messages.printError("Required properties cannot be primitives", variableElement);
            return false;
        }

        return true;
    }

    private boolean isValidPackedProperty(VariableElement variableElement, ProtobufProperty propertyAnnotation) {
        if(!propertyAnnotation.packed() || types.isSubType(variableElement.asType(), Collection.class)) {
            return true;
        }

        messages.printError("Only scalar properties can be packed", variableElement);
        return false;
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

    private long processEnumConstants(ProtobufObjectElement messageElement) {
        var enumTree = (ClassTree) trees.getTree(messageElement.element());
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
            messages.printError("Missing protobuf enum constructor: an enum should provide a constructor with a scalar parameter annotated with @ProtobufEnumIndex", enumElement);
            return Optional.empty();
        }

        if(metadata.get().isUnknown()) {
            return Optional.empty();
        }

        var result = new ProtobufObjectElement(enumElement, metadata.get(), null);
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
            messages.printError("%s's index must be a positive".formatted(variableName), enumElement);
            return;
        }

        var error = messageElement.addConstant(value, variableName);
        if(error.isEmpty()) {
            return;
        }

        messages.printError("Duplicated enum constant: %s and %s with index %s".formatted(variableName, error.get(), value), enumElement);
    }
}
