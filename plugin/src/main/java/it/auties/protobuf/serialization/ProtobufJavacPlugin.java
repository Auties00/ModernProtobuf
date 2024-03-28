package it.auties.protobuf.serialization;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.object.ProtobufEnumMetadata;
import it.auties.protobuf.serialization.object.ProtobufMessageElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.Converters;
import it.auties.protobuf.serialization.support.Messages;
import it.auties.protobuf.serialization.support.Types;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@SupportedAnnotationTypes({
        "it.auties.protobuf.annotation.ProtobufProperty",
        "it.auties.protobuf.annotation.ProtobufConverter",
        "it.auties.protobuf.annotation.ProtobufBuilder",
        "it.auties.protobuf.annotation.ProtobufEnumIndex"
})
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
        roundEnv.getElementsAnnotatedWith(ProtobufConverter.class)
                .stream()
                .map(entry -> (ExecutableElement) entry)
                .forEach(this::checkConverter);
        roundEnv.getElementsAnnotatedWith(ProtobufBuilder.class)
                .stream()
                .map(entry -> (ExecutableElement) entry)
                .forEach(this::checkBuilder);
    }

    // Checks if a method annotated with @ProtobufBuilder is a valid candidate
    // Builders must be constructors or static fields with non-private visibility
    private void checkBuilder(ExecutableElement entry) {
        if(entry.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufBuilder must have at least package-private visibility", entry);
        }else if(entry.getKind() != ElementKind.CONSTRUCTOR && !entry.getModifiers().contains(Modifier.STATIC)) {
            messages.printError("Illegal method: a method annotated with @ProtobufBuilder must be a constructor or static", entry);
        }
    }

    // Checks if a method annotated with @ProtobufConverter is a valid candidate
    // Converters must be constructors or static fields with non-private visibility that take a single parameter
    // Or non-static methods with non-private visibility that don't take any parameter
    private void checkConverter(ExecutableElement entry) {
        if(entry.getModifiers().contains(Modifier.PRIVATE)) {
            messages.printError("Weak visibility: a method annotated with @ProtobufConverter must have at least package-private visibility", entry);
            return;
        }

        if((entry.getKind() == ElementKind.CONSTRUCTOR && entry.getModifiers().contains(Modifier.STATIC)) && entry.getParameters().size() != 1) {
            messages.printError("Illegal method: a static method annotated with @ProtobufConverter must have a single parameter", entry);
        }else if(!entry.getParameters().isEmpty()) {
            messages.printError("Illegal method: a non-static method annotated with @ProtobufConverter mustn't have parameters", entry);
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
        TypeElement currentElement;
        try {
            var objects = getProtobufObjects(roundEnv);
            for(var object : objects) {
                currentElement = object;
                var result = processElement(object);
                if (result.isEmpty()) {
                    continue;
                }

                var packageName = processingEnv.getElementUtils().getPackageOf(result.get().element());
                createSpecClass(result.get(), packageName);
                if(result.get().isEnum()){
                    continue;
                }

                createBuilderClass(result.get(), null, packageName);
                for (var builder : result.get().builders()) {
                    createBuilderClass(result.get(), builder, packageName);
                }
            }
        }catch (IOException exception) {
            messages.printWarning("An error occurred: " + exception.getMessage(), currentElement);
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

    private TypeMirror getAccessorType(Element accessor) {
        return switch (accessor) {
            case VariableElement element -> element.asType();
            case ExecutableElement element -> element.getReturnType();
            default -> throw new IllegalStateException("Unexpected value: " + accessor);
        };
    }

    private Optional<Element> getMixin(ProtobufProperty property) {
        try {
            var implementation = property.mixin();
            var type = processingEnv.getElementUtils().getTypeElement(implementation.getName());
            if(type == null || types.isSameType(type.asType(), ProtobufMixin.class)) {
                return Optional.empty();
            }

            return Optional.of(type);
        }catch (MirroredTypeException exception) {
            if (!(exception.getTypeMirror() instanceof DeclaredType declaredType)) {
                return Optional.empty();
            }

            if(types.isSameType(declaredType, ProtobufMixin.class)) {
                return Optional.empty();
            }

            return Optional.of(declaredType.asElement());
        }
    }

    private Optional<ProtobufPropertyType> getImplementationType(Element element, TypeMirror elementType, ProtobufProperty property) {
        var mixin = getMixin(property).orElse(null);
        var override = getMirroredOverride(property::overrideType);
        if (types.isSubType(elementType, Collection.class)
                || !Objects.equals(property.overrideRepeatedType(), Object.class)) {
            var collectionTypeParameter = getTypeParameter(elementType, types.getType(Collection.class), 0);
            if (override.isEmpty() && collectionTypeParameter.isEmpty()) {
                messages.printError("Type inference error: cannot determine collection's type parameter. Specify the type explicitly in @ProtobufProperty with overrideType", element);
                return Optional.empty();
            }

            var concreteCollectionType = getMirroredOverride(property::overrideRepeatedType)
                    .or(() -> getConcreteCollectionType(elementType));
            if(concreteCollectionType.isEmpty()) {
                messages.printError("Type inference error: cannot determine concrete collection type. Use a concrete type for the property's field(for example ArrayList instead of List) or specify the type explicitly in @ProtobufProperty with overrideCollectionType", element);
                return Optional.empty();
            }

            var implementationType = override.orElseGet(collectionTypeParameter::get);
            var value = new ProtobufPropertyType.NormalType(
                    property.type(),
                    collectionTypeParameter.orElseGet(override::get),
                    implementationType,
                    types.isEnum(implementationType)
            );
            var type = new ProtobufPropertyType.CollectionType(
                    elementType,
                    concreteCollectionType.get(),
                    value
            );
            attributeConverters(
                    element,
                    property.type(),
                    implementationType,
                    value,
                    mixin
            );
            return Optional.of(type);
        }

        if(types.isSubType(elementType, Map.class)
                || !Objects.equals(property.overrideMapType(), Object.class)
                || property.mapKeyType() != ProtobufType.MAP
                || !Objects.equals(property.overrideMapKeyType(), Object.class)
                || property.mapValueType() != ProtobufType.MAP
                || !Objects.equals(property.overrideMapValueType(), Object.class)) {
            var concreteMapType = getConcreteMapType(
                    property,
                    element,
                    elementType
            );
            if (concreteMapType.isEmpty()) {
                return Optional.empty();
            }

            attributeConverters(element, concreteMapType.get().keyType().protobufType(), concreteMapType.get().keyType().descriptorElementType(), concreteMapType.get().keyType(), mixin);
            attributeConverters(element, concreteMapType.get().valueType().protobufType(), concreteMapType.get().valueType().descriptorElementType(), concreteMapType.get().valueType(), mixin);
            return Optional.of(concreteMapType.get());
        }

        var rawFieldType = types.erase(elementType);
        var implementation = new ProtobufPropertyType.NormalType(property.type(), elementType, override.orElse(elementType), types.isEnum(rawFieldType));
        attributeConverters(element, property.type(), elementType, implementation, mixin);
        return Optional.of(implementation);
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

    private Optional<TypeMirror> getConcreteCollectionType(TypeMirror elementType) {
        if(!(elementType instanceof DeclaredType declaredFieldType)) {
            return Optional.empty();
        }

        var fieldTypeElement = (TypeElement) declaredFieldType.asElement();
        if(!fieldTypeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return Optional.of(elementType);
        }

        var typeArguments = declaredFieldType.getTypeArguments() != null ? declaredFieldType.getTypeArguments().toArray(TypeMirror[]::new) : new TypeMirror[0];
        if(types.isSameType(elementType, Collection.class) || types.isSubType(elementType, List.class)) {
            return Optional.ofNullable(types.newType(ArrayList.class, typeArguments));
        }else if(types.isSubType(elementType, Set.class)) {
            return Optional.ofNullable(types.newType(HashSet.class, typeArguments));
        }else if(types.isSubType(elementType, Queue.class) || types.isSubType(elementType, Deque.class)) {
            return Optional.ofNullable(types.newType(LinkedList.class, typeArguments));
        }else {
            return Optional.empty();
        }
    }

    // This method is used to get all the metadata about a property with type MAP
    // One might ask why we don't check if the type is a map
    // The reason is that there are cases where a user could want to use a collection or any other object and have a mixin in between to convert them
    private Optional<ProtobufPropertyType.MapType> getConcreteMapType(ProtobufProperty property, Element element, TypeMirror elementType) {
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
        var keyEntry = new ProtobufPropertyType.NormalType(
                property.mapKeyType(), // Just the proto type of the key
                keyTypeParameter.or(() -> keyTypeOverride).orElse(null), // Follow hierarchy explained in ProtobufPropertyType
                keyTypeOverride.or(() -> keyTypeParameter).orElse(null), // Follow hierarchy explained in ProtobufPropertyType
                false // Objects can't be keys, so enums can't as well
        );

        // Same thing but for the value type
        var valueTypeOverride = getMirroredOverride(property::overrideMapValueType);
        var valueTypeParameter = getTypeParameter(elementType, types.getType(Map.class), 1);
        if (valueTypeOverride.isEmpty() && valueTypeParameter.isEmpty()) {
            messages.printError("Type inference error: cannot determine map's value type. Specify the type explicitly in @ProtobufProperty with overrideValueType", element);
            error = true;
        }

        var valueEntryTypeImplementation = valueTypeOverride.or(() -> valueTypeParameter).orElse(null);
        var valueEntry = new ProtobufPropertyType.NormalType(
                property.mapValueType(), // Just the proto type
                valueTypeParameter.or(() -> valueTypeOverride).orElse(null),  // Follow hierarchy explained in ProtobufPropertyType
                valueEntryTypeImplementation,  // Follow hierarchy explained in ProtobufPropertyType
                types.isEnum(valueEntryTypeImplementation) // Values can be enums so we have to check
        );

        // If the type isn't a declared type(ex. a primitive) or if the type is not a map(ex. a collection)
        // Use the overrideMapType or an HashMap
        // This is useful for example if the user is using a mixin to convert between a Map and a Collection
        if(!(elementType instanceof DeclaredType declaredFieldType) || !types.isSubType(declaredFieldType, Map.class)) {
            if(error) {
                return Optional.empty();
            }

            var hashMapType = types.newType(HashMap.class);
            return Optional.of(new ProtobufPropertyType.MapType(
                    elementType,
                    hashMapType,
                    keyEntry,
                    valueEntry
            ));
        }

        // If the map type is not abstract, create the type as we would with a normal type
        var mapTypeOverride = getMirroredOverride(property::overrideMapType);
        var mapType = getMapType(elementType, declaredFieldType);
        if(mapTypeOverride.isEmpty() && mapType.isEmpty()) {
            messages.printError("Type inference error: cannot determine concrete map type. Use a concrete type for the property's field(for example HashMap instead of Map) or specify the type explicitly in @ProtobufProperty with overrideMapType", element);
            return Optional.empty();
        }

        return Optional.of(new ProtobufPropertyType.MapType(
                mapType.or(() -> mapTypeOverride).get(),  // Follow hierarchy explained in ProtobufPropertyType
                mapTypeOverride.or(() -> mapType).get(),  // Follow hierarchy explained in ProtobufPropertyType
                keyEntry,
                valueEntry
        ));
    }

    // Get the implementation for the map type
    // This method assumes that the type has already been checked and is a map
    private Optional<TypeMirror> getMapType(TypeMirror elementType, DeclaredType declaredFieldType) {
        // Considering the method assumptions, we can cast TypeElement to the type
        var fieldTypeElement = (TypeElement) declaredFieldType.asElement();

        // If the map type is not abstract, just return it
        if(!fieldTypeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return Optional.of(fieldTypeElement.asType());
        }

        // Keep the type arguments from the abstract type
        var typeArguments = declaredFieldType.getTypeArguments() != null ? declaredFieldType.getTypeArguments().toArray(TypeMirror[]::new) : new TypeMirror[0];

        // Return the right implementation
        if(types.isSubType(elementType, ConcurrentMap.class)) {
            return Optional.of(types.newType(ConcurrentHashMap.class, typeArguments));
        } else if(types.isSubType(elementType, SequencedMap.class)) {
            return Optional.of(types.newType(LinkedHashMap.class, typeArguments));
        }  else if(types.isSubType(elementType, NavigableMap.class) || types.isSubType(elementType, SortedMap.class)) {
            return Optional.of(types.newType(TreeMap.class, typeArguments));
        } else if(types.isSameType(elementType, Map.class)) {
            return Optional.of(types.newType(HashMap.class, typeArguments));
        }else {
            return Optional.empty();
        }
    }

    private void attributeConverters(Element invoker, ProtobufType from, TypeMirror to, ProtobufPropertyType implementation, Element mixin) {
        var fromType = types.getType(from.wrappedType());
        if(!(to instanceof DeclaredType declaredType)) {
            return;
        }

        if(!types.isSubType(to, fromType)
                || (from == ProtobufType.OBJECT && !types.isSubType(to, ProtobufMessage.class) && !types.isSubType(to, ProtobufEnum.class))) {
            ExecutableElement serializer = null;
            ExecutableElement deserializer = null;
            var convertersWrapper = Objects.requireNonNullElse(mixin, declaredType.asElement());
            for (var entry : convertersWrapper.getEnclosedElements()) {
                if (serializer != null && deserializer != null) {
                    break;
                }

                if (!(entry instanceof ExecutableElement element)) {
                    continue;
                }

                if (converters.isDeserializer(element, fromType)) {
                    deserializer = element;
                } else if (converters.isSerializer(element, to, fromType)) {
                    serializer = element;
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

            if (deserializer != null) {
                implementation.addNullableConverter(new ProtobufDeserializerElement(deserializer));
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
