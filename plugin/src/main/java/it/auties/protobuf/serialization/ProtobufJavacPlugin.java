package it.auties.protobuf.serialization;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.generator.clazz.ProtobufBuilderVisitor;
import it.auties.protobuf.serialization.generator.clazz.ProtobufSpecVisitor;
import it.auties.protobuf.serialization.object.ProtobufEnumMetadata;
import it.auties.protobuf.serialization.object.ProtobufMessageElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.Converters;
import it.auties.protobuf.serialization.support.Messages;
import it.auties.protobuf.serialization.support.Types;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@SupportedAnnotationTypes({
        "it.auties.protobuf.annotation.ProtobufProperty",
        "it.auties.protobuf.annotation.ProtobufConverter",
        "it.auties.protobuf.annotation.ProtobufBuilder",
        "it.auties.protobuf.annotation.ProtobufEnumIndex"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ProtobufJavacPlugin extends AbstractProcessor {
    private Trees trees;
    private Types types;
    private Messages messages;
    private Converters converters;


    // Called when the annotation processor gets initialized
    @Override
    public synchronized void init(ProcessingEnvironment wrapperProcessingEnv) {
        var unwrappedProcessingEnv = unwrapProcessingEnv(wrapperProcessingEnv);
        super.init(unwrappedProcessingEnv);
        this.trees = Trees.instance(processingEnv);
        this.types = new Types(processingEnv);
        this.messages = new Messages(processingEnv);
        this.converters = new Converters(types);
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
        checkEnclosing(
                roundEnv,
                ProtobufMessage.class,
                ProtobufProperty.class,
                "Illegal enclosing class: a field annotated with @ProtobufProperty should be enclosed by a class or record that implements ProtobufMessage"
        );

        checkEnclosing(
                roundEnv,
                ProtobufMessage.class,
                ProtobufGetter.class,
                "Illegal enclosing class: a method annotated with @ProtobufGetter should be enclosed by a class or record that implements ProtobufMessage"
        );

        checkEnclosing(
                roundEnv,
                ProtobufEnum.class,
                ProtobufEnumIndex.class,
                "Illegal enclosing class: a field or parameter annotated with @ProtobufEnumIndex should be enclosed by an enum that implements ProtobufEnum"
        );

        checkEnclosing(
                roundEnv,
                ProtobufMixin.class,
                ProtobufDefaultValue.class,
                "Illegal enclosing class: a method annotated with @ProtobufDefaultValue should be enclosed by a class that implements ProtobufMixin"
        );

        roundEnv.getElementsAnnotatedWith(ProtobufSerializer.class)
                .forEach(this::checkSerializer);

        roundEnv.getElementsAnnotatedWith(ProtobufDeserializer.class)
                .forEach(this::checkDeserializer);

        roundEnv.getElementsAnnotatedWith(ProtobufBuilder.class)
                .forEach(this::checkBuilder);

        roundEnv.getElementsAnnotatedWith(ProtobufDefaultValue.class)
                .forEach(this::checkDefaultValue);
    }


    private void checkDefaultValue(Element entry) {
        if(!(entry instanceof ExecutableElement)) {
            messages.printError("Invalid element: only methods can be annotated with @ProtobufDefaultValue", entry);
            return;
        }

        if(entry.getModifiers().contains(Modifier.PUBLIC)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufDefaultValue must have at least public visibility", entry);
            return;
        }

        if(!entry.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufDefaultValue must be static", entry);
        }
    }


    // Checks if a method annotated with @ProtobufBuilder is a valid candidate
    // Builders must be constructors or static fields with non-private visibility
    private void checkBuilder(Element element) {
        if(!(element instanceof ExecutableElement)) {
            messages.printError("Invalid element: only methods can be annotated with @ProtobufBuilder", element);
            return;
        }

        if(element.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufBuilder must have at least package-private visibility", element);
            return;
        }

        if(element.getKind() != ElementKind.CONSTRUCTOR && !element.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufBuilder must be a constructor or static", element);
        }
    }

    // Checks if a method annotated with @ProtobufSerializer is a valid candidate
    private void checkSerializer(Element element) {
        if(!(element instanceof ExecutableElement executableElement)) {
            messages.printError("Invalid element: only methods can be annotated with @ProtobufSerializer", element);
            return;
        }

        if(executableElement.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufSerializer must have at least package-private visibility", executableElement);
            return;
        }

        if(executableElement.getKind() == ElementKind.CONSTRUCTOR || executableElement.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufSerializer must be a non-static method", executableElement);
            return;
        }

        if(!executableElement.getParameters().isEmpty()) {
            messages.printError("Illegal method: a method annotated with @ProtobufSerializer mustn't take any parameters", executableElement);
        }
    }

    // Checks if a method annotated with @ProtobufDeserializer is a valid candidate
    private void checkDeserializer(Element element) {
        if(!(element instanceof ExecutableElement executableElement)) {
            messages.printError("Invalid element: only methods can be annotated with @ProtobufDeserializer", element);
            return;
        }

        if(executableElement.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufDeserializer must have at least package-private visibility", executableElement);
            return;
        }

        if(executableElement.getKind() != ElementKind.CONSTRUCTOR && !executableElement.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer must be static or a constructor", executableElement);
            return;
        }

        if(executableElement.getParameters().size() > 1) {
            messages.printError("Illegal method: a method annotated with @ProtobufDeserializer must take exactly zero or one parameter", executableElement);
        }
    }

    // Utility method to check the scope
    private void checkEnclosing(RoundEnvironment roundEnv, Class<?> type, Class<? extends Annotation> annotation, String error) {
        roundEnv.getElementsAnnotatedWith(annotation)
                .stream()
                .filter(property -> !types.isSubType(getEnclosingTypeElement(property).asType(), type))
                .forEach(property -> messages.printError(error, property));
    }

    // Get the earliest TypeElement that wraps the input element
    private TypeElement getEnclosingTypeElement(Element element) {
        Objects.requireNonNull(element);
        if(element instanceof TypeElement typeElement) {
            return typeElement;
        }

        return getEnclosingTypeElement(element.getEnclosingElement());
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
        return getElements(roundEnv.getRootElements())
                .stream()
                .filter(entry -> types.isSubType(entry.asType(), ProtobufObject.class))
                .toList();
    }

    private Set<TypeElement> getElements(Collection<? extends Element> elements) {
        var results = new HashSet<TypeElement>();
        for(var element : elements) {
            if(element instanceof TypeElement typeElement) {
                results.add(typeElement);
                results.addAll(getElements(typeElement.getEnclosedElements()));
            }
        }

        return results;
    }

    private Optional<ProtobufMessageElement> processElement(TypeElement object) {
        if(object.getModifiers().contains(Modifier.ABSTRACT)) {
            return Optional.empty();
        }

        return switch (object.getKind()) {
            case ENUM -> processEnum(object);
            case RECORD, CLASS -> processMessage(object);
            default -> Optional.empty();
        };
    }

    private Optional<ProtobufMessageElement> processMessage(TypeElement message) {
        var messageElement = new ProtobufMessageElement(message, null);
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


    private void processMessage(ProtobufMessageElement messageElement, TypeElement typeElement) {
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

    private void processMethod(ProtobufMessageElement messageElement, ExecutableElement executableElement) {
        var builder = executableElement.getAnnotation(ProtobufBuilder.class);
        if(builder != null) {
            messageElement.addBuilder(builder.className(), executableElement.getParameters(), executableElement);
        }
    }

    private boolean hasPropertiesConstructor(ProtobufMessageElement message) {
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

    private void processProperty(ProtobufMessageElement messageElement, VariableElement variableElement) {
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

    private Optional<String> getDefaultValue(TypeMirror type, List<TypeElement> mixins) {
        for(var mixin : mixins) {
            ExecutableElement defaultValueProvider = null;
            var defaultValueProviderCandidates = new ArrayList<ExecutableElement>();
            for(var element : mixin.getEnclosedElements()) {
                if(!(element instanceof ExecutableElement executableElement)) {
                    continue;
                }

                var annotation = executableElement.getAnnotation(ProtobufDefaultValue.class);
                if(annotation == null) {
                    continue;
                }

                if(types.isSameType(type, executableElement.getReturnType())) {
                    defaultValueProvider = executableElement;
                    break;
                }

                if(types.isSubType(type, executableElement.getReturnType())){
                    defaultValueProviderCandidates.add(executableElement);
                }
            }

            if(defaultValueProvider != null) {
                return Optional.of(mixin.getQualifiedName() + "." + defaultValueProvider.getSimpleName() + "()");
            }

            var bestMatch = defaultValueProviderCandidates.stream()
                    .reduce((first, second) -> types.isSubType(first.getReturnType(), second.getReturnType()) ? first : second);
            if(bestMatch.isPresent()) {
                var bestMatchOwner = (TypeElement) bestMatch.get().getEnclosingElement();
                return Optional.of(bestMatchOwner.getQualifiedName() + "." + bestMatch.get().getSimpleName() + "()");
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
            var collectionDefaultValue = getCollectionDefaultValue(collectionType, mixins);
            if(collectionDefaultValue == null) {
                messages.printError("Type inference error: cannot determine collection's type. Specify the type explicitly in @ProtobufProperty with overrideRepeatedType", element);
                return Optional.empty();
            }

            var implementationType = override.orElseGet(collectionTypeParameter::get);
            var collectionTypeParameterDefaultValue = getDefaultValue(implementationType, mixins)
                    .orElse("null");
            var collectionTypeParameterType = new ProtobufPropertyType.NormalType(
                    property.type(),
                    collectionTypeParameter.orElseGet(override::get),
                    implementationType,
                    collectionTypeParameterDefaultValue,
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

        var rawFieldType = types.erase(elementType);
        var implementationType = override.orElse(elementType);
        var implementation = new ProtobufPropertyType.NormalType(
                property.type(),
                elementType,
                implementationType,
                getDefaultValue(implementationType, mixins).orElse("null"),
                types.isEnum(rawFieldType)
        );
        attributeConverters(element, property.type(), elementType, implementation, mixins);
        return Optional.of(implementation);
    }

    private String getCollectionDefaultValue(TypeMirror collectionType, List<TypeElement> mixins) {
        if (collectionType instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement
                && !typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return "new %s()".formatted(typeElement.getQualifiedName());
        }

        return getDefaultValue(collectionType, mixins).orElse(null);
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
        var keyDefaultValue = getDefaultValue(keyImplementationType, mixins)
                .orElse("null");
        var keyEntry = new ProtobufPropertyType.NormalType(
                property.mapKeyType(), // Just the proto type of the key
                keyDescriptorType, // Follow hierarchy explained in ProtobufPropertyType
                keyImplementationType, // Follow hierarchy explained in ProtobufPropertyType
                keyDefaultValue,
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
        var valueDefaultValue = getDefaultValue(valueImplementationType, mixins)
                .orElse("null");
        var valueEntry = new ProtobufPropertyType.NormalType(
                property.mapValueType(), // Just the proto type
                valueDescriptorType,  // Follow hierarchy explained in ProtobufPropertyType
                valueImplementationType,  // Follow hierarchy explained in ProtobufPropertyType
                valueDefaultValue,
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
        var mapDefaultValue = getDefaultValue(mapType, mixins).orElseThrow();
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

    private void attributeConverters(Element invoker, ProtobufType from, TypeMirror to, ProtobufPropertyType implementation, List<TypeElement> mixins) {
        var fromType = types.getType(from.wrappedType());
        if(!(to instanceof DeclaredType declaredType)) {
            return;
        }

        if(!types.isSubType(to, fromType)
                || (from == ProtobufType.OBJECT && !types.isSubType(to, ProtobufMessage.class) && !types.isSubType(to, ProtobufEnum.class))) {
            ExecutableElement serializer = null;
            var deserializers = new ArrayList<ProtobufDeserializerElement>();
            var candidates = new ArrayList<>(mixins);
            candidates.add((TypeElement) declaredType.asElement());
            for(var candidate : candidates) {
                for(var entry : candidate.getEnclosedElements()) {
                    if (serializer != null && !deserializers.isEmpty()) {
                        break;
                    }

                    if (!(entry instanceof ExecutableElement element)) {
                        continue;
                    }

                    if (converters.isSerializer(element, to, fromType)) {
                        serializer = element;
                        continue;
                    }

                    converters.getDeserializerBuilderBehaviour(element, to, fromType).ifPresent(builderBehaviour -> {
                        deserializers.add(new ProtobufDeserializerElement(
                                element,
                                fromType,
                                builderBehaviour
                        ));
                    });
                }
            }

            if (serializer != null) {
                implementation.addNullableConverter(new ProtobufSerializerElement(
                        serializer,
                        serializer.getReturnType().getKind().isPrimitive()
                ));
            } else {
                messages.printError("Missing converter: cannot find a serializer for %s".formatted(to), invoker);
            }

            if (!deserializers.isEmpty()) {
                var bestDeserializer = deserializers.stream()
                        .reduce((first, second) -> types.isSubType(first.element().getParameters().getFirst().asType(), second.element().getParameters().getFirst().asType()) ? first : second)
                        .orElseThrow();
                implementation.addNullableConverter(bestDeserializer);
            } else {
                messages.printError("Missing converter: cannot find a deserializer for %s".formatted(fromType), invoker);
            }
        }
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

    private Optional<ProtobufMessageElement> processEnum(TypeElement enumElement) {
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

    private long processEnumConstants(ProtobufMessageElement messageElement) {
        var enumTree = (ClassTree) trees.getTree(messageElement.element());
        return enumTree.getMembers()
                .stream()
                .filter(member -> member instanceof VariableTree)
                .map(member -> (VariableTree) member)
                .peek(variableTree -> processEnumConstant(messageElement, messageElement.element(), variableTree))
                .count();
    }

    private Optional<ProtobufMessageElement> createEnumElement(TypeElement enumElement) {
        var metadata = getEnumMetadata(enumElement);
        if (metadata.isEmpty()) {
            messages.printError("Missing protobuf enum constructor: an enum should provide a constructor with a scalar parameter annotated with @ProtobufEnumIndex", enumElement);
            return Optional.empty();
        }

        if(metadata.get().isUnknown()) {
            return Optional.empty();
        }

        var result = new ProtobufMessageElement(enumElement, metadata.get());
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

    private void processEnumConstant(ProtobufMessageElement messageElement, TypeElement enumElement, VariableTree enumConstantTree) {
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
