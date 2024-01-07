package it.auties.protobuf.serialization;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.extension.OptionalExtension;
import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufObject;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.instrumentation.ProtobufDeserializationVisitor;
import it.auties.protobuf.serialization.instrumentation.ProtobufSerializationVisitor;
import it.auties.protobuf.serialization.object.ProtobufBuilderElement;
import it.auties.protobuf.serialization.object.ProtobufEnumMetadata;
import it.auties.protobuf.serialization.object.ProtobufMessageElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyStub;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

@SupportedAnnotationTypes({
        "it.auties.protobuf.annotation.ProtobufProperty",
        "it.auties.protobuf.annotation.ProtobufEnumIndex"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ProtobufJavacPlugin extends AbstractProcessor {
    private Trees trees;
    private DeclaredType protoObjectType;
    private DeclaredType protoMessageType;
    private DeclaredType protoMixinType;
    private DeclaredType objectType;
    private DeclaredType protoEnumType;
    private DeclaredType collectionType;
    private DeclaredType mapType;
    private DeclaredType concurrentMapType;
    private DeclaredType listType;
    private TypeElement arrayListTypeElement;
    private DeclaredType setType;
    private TypeElement hashSetTypeElement;
    private DeclaredType queueType;
    private DeclaredType dequeType;
    private TypeElement linkedListTypeElement;
    private TypeElement hashMapElement;
    private TypeElement concurrentHashMapElement;
    private PrimitiveType intType;
    private Map<String, ProtobufSerializerElement> optionalSerializers;
    private Map<String, ProtobufDeserializerElement> optionalDeserializers;
    private Map<String, ProtobufSerializerElement> atomicSerializers;
    private Map<String, ProtobufDeserializerElement> atomicDeserializers;

    @Override
    public synchronized void init(ProcessingEnvironment wrapperProcessingEnv) {
        var unwrappedProcessingEnv = unwrapProcessingEnv(wrapperProcessingEnv);
        super.init(unwrappedProcessingEnv);
        this.trees = Trees.instance(processingEnv);
        initTypes();
        initOptionalConverters();
        initAtomicConverters();
    }

    private void initTypes() {
        this.protoObjectType = (DeclaredType) getType(ProtobufObject.class);
        this.protoMessageType = (DeclaredType) getType(ProtobufMessage.class);
        this.protoEnumType = (DeclaredType) getType(ProtobufEnum.class);
        this.objectType = (DeclaredType) getType(Object.class);
        this.intType = (PrimitiveType) getType(int.class);
        this.collectionType = (DeclaredType) erase(getType(Collection.class));
        this.mapType = (DeclaredType) erase(getType(Map.class));
        this.concurrentMapType = (DeclaredType) erase(getType(ConcurrentMap.class));
        this.listType = (DeclaredType) erase(getType(List.class));
        var arrayListType = (DeclaredType) erase(getType(ArrayList.class));
        this.arrayListTypeElement = (TypeElement) arrayListType.asElement();
        this.setType = (DeclaredType) erase(getType(Set.class));
        this.protoMixinType = (DeclaredType) erase(getType(ProtobufMixin.class));
        var hashSetType = (DeclaredType) erase(getType(HashSet.class));
        this.arrayListTypeElement = (TypeElement) arrayListType.asElement();
        this.hashSetTypeElement = (TypeElement) hashSetType.asElement();
        this.queueType = (DeclaredType) erase(getType(Queue.class));
        this.dequeType = (DeclaredType) erase(getType(Deque.class));
        var linkedListType = (DeclaredType) erase(getType(LinkedList.class));
        this.linkedListTypeElement = (TypeElement) linkedListType.asElement();
        var hashMapType = (DeclaredType) erase(getType(HashMap.class));
        this.hashMapElement = (TypeElement) hashMapType.asElement();
        var concurrentHashMapType = (DeclaredType) erase(getType(ConcurrentHashMap.class));
        this.concurrentHashMapElement = (TypeElement) concurrentHashMapType.asElement();
    }

    private void initAtomicConverters() {
        var atomicReferenceType = (DeclaredType) getType(AtomicReference.class);
        var atomicReferenceDeserializer = getMandatoryMethod(atomicReferenceType, "<init>");
        var atomicReferenceSerializer = getMandatoryMethod(atomicReferenceType, "get");
        var atomicIntegerType = (DeclaredType) getType(AtomicInteger.class);
        var atomicIntegerDeserializer = getMandatoryMethod(atomicIntegerType, "<init>");
        var atomicIntegerSerializer = getMandatoryMethod(atomicIntegerType, "get");
        var atomicLongType = (DeclaredType) getType(AtomicLong.class);
        var atomicLongDeserializer = getMandatoryMethod(atomicLongType, "<init>");
        var atomicLongSerializer = getMandatoryMethod(atomicLongType, "get");
        var atomicBooleanType = (DeclaredType) getType(AtomicBoolean.class);
        var atomicBooleanDeserializer = getMandatoryMethod(atomicBooleanType, "<init>");
        var atomicBooleanSerializer = getMandatoryMethod(atomicBooleanType, "get");
        this.atomicSerializers = Map.of(
                AtomicReference.class.getName(),
                new ProtobufSerializerElement(atomicReferenceSerializer, false, false),
                AtomicInteger.class.getName(),
                new ProtobufSerializerElement(atomicIntegerSerializer, true, false),
                AtomicLong.class.getName(),
                new ProtobufSerializerElement(atomicLongSerializer, true, false),
                AtomicBoolean.class.getName(),
                new ProtobufSerializerElement(atomicBooleanSerializer, true, false)
        );
        this.atomicDeserializers = Map.of(
                AtomicReference.class.getName(),
                new ProtobufDeserializerElement(atomicReferenceDeserializer),
                AtomicInteger.class.getName(),
                new ProtobufDeserializerElement(atomicIntegerDeserializer),
                AtomicLong.class.getName(),
                new ProtobufDeserializerElement(atomicLongDeserializer),
                AtomicBoolean.class.getName(),
                new ProtobufDeserializerElement(atomicBooleanDeserializer)
        );
    }

    private void initOptionalConverters() {
        var optionalType = (DeclaredType) getType(Optional.class);
        var optionalExtensionType = (DeclaredType) getType(OptionalExtension.class);
        var optionalDeserializer = getMandatoryMethod(optionalType, "ofNullable");
        var optionalSerializer = getMandatoryMethod(optionalType, "orElse");
        var optionalIntDeserializer = getMandatoryMethod(optionalExtensionType, "ofNullableInt");
        var optionalIntSerializer = getMandatoryMethod(optionalExtensionType, "toNullableInt");
        var optionalLongDeserializer = getMandatoryMethod(optionalExtensionType, "ofNullableLong");
        var optionalLongSerializer = getMandatoryMethod(optionalExtensionType, "toNullableLong");
        var optionalDoubleDeserializer = getMandatoryMethod(optionalExtensionType, "ofNullableDouble");
        var optionalDoubleSerializer = getMandatoryMethod(optionalExtensionType, "toNullableDouble");
        this.optionalSerializers = Map.of(
                Optional.class.getName(),
                new ProtobufSerializerElement(optionalSerializer, false, false,"null"),
                OptionalInt.class.getName(),
                new ProtobufSerializerElement(optionalIntSerializer, false,false),
                OptionalLong.class.getName(),
                new ProtobufSerializerElement(optionalLongSerializer, false, false),
                OptionalDouble.class.getName(),
                new ProtobufSerializerElement(optionalDoubleSerializer, false, false)
        );
        this.optionalDeserializers = Map.of(
                Optional.class.getName(),
                new ProtobufDeserializerElement(optionalDeserializer),
                OptionalInt.class.getName(),
                new ProtobufDeserializerElement(optionalIntDeserializer),
                OptionalLong.class.getName(),
                new ProtobufDeserializerElement(optionalLongDeserializer),
                OptionalDouble.class.getName(),
                new ProtobufDeserializerElement(optionalDoubleDeserializer)
        );
    }

    private ExecutableElement getMandatoryMethod(DeclaredType type, String method) {
        return type.asElement()
                .getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof ExecutableElement)
                .map(entry -> (ExecutableElement) entry)
                .filter(entry -> entry.getSimpleName().contentEquals(method))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodError("Missing method " + method));
    }

    private ProcessingEnvironment unwrapProcessingEnv(ProcessingEnvironment wrapper) {
        try {
            var apiWrappers = wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
            var unwrapMethod = apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            return (ProcessingEnvironment) unwrapMethod.invoke(null, ProcessingEnvironment.class, wrapper);
        } catch (ReflectiveOperationException exception) {
            return wrapper;
        }
    }

    private TypeMirror getType(Class<?> type) {
        if(type.isPrimitive()) {
            var kind = TypeKind.valueOf(type.getName().toUpperCase(Locale.ROOT));
            return processingEnv.getTypeUtils().getPrimitiveType(kind);
        }

        if(type.isArray()) {
            return processingEnv.getTypeUtils().getArrayType(getType(type.getComponentType()));
        }

        var result = processingEnv.getElementUtils().getTypeElement(type.getName());
        return result.asType();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        checkAnnotations(roundEnv);
        processObjects(roundEnv);
        return true;
    }

    private void checkAnnotations(RoundEnvironment roundEnv) {
        checkEnclosing(
                roundEnv,
                protoMessageType,
                ProtobufProperty.class,
                "Illegal enclosing class: a field annotated with @ProtobufProperty should be enclosed by a class or record that implements ProtobufMessage"
        );
        checkEnclosing(
                roundEnv,
                protoMessageType,
                ProtobufGetter.class,
                "Illegal enclosing class: a method annotated with @ProtobufGetter should be enclosed by a class or record that implements ProtobufMessage"
        );
        checkEnclosing(
                roundEnv,
                protoEnumType,
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

    private void checkBuilder(ExecutableElement entry) {
        if(entry.getModifiers().contains(Modifier.PRIVATE)) {
            printError("Weak visibility: a method annotated with @ProtobufBuilder must have at least package-private visibility", entry);
            return;
        }
        
        if(entry.getModifiers().contains(Modifier.STATIC)) {
            return;
        }

        printError("Illegal method: a method annotated with @ProtobufBuilder must be static", entry);
    }
    
    private void checkConverter(ExecutableElement entry) {
        if(entry.getModifiers().contains(Modifier.PRIVATE)) {
            printError("Weak visibility: a method annotated with @ProtobufConverter must have at least package-private visibility", entry);
            return;
        }
        
        var isStatic = entry.getModifiers().contains(Modifier.STATIC);
        if(isStatic && entry.getParameters().size() != 1) {
            printError("Illegal method: a static method annotated with @ProtobufConverter must have a single parameter", entry);
        }else if(!isStatic && !entry.getParameters().isEmpty()) {
            printError("Illegal method: a non-static method annotated with @ProtobufConverter mustn't have parameters", entry);
        }
    }

    private void checkEnclosing(RoundEnvironment roundEnv, TypeMirror type, Class<? extends Annotation> annotation, String error) {
        roundEnv.getElementsAnnotatedWith(annotation)
                .stream()
                .filter(property -> !isSubType(getEnclosingTypeElement(property).asType(), type))
                .forEach(property -> printError(error, property));
    }

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
            printWarning("An error occurred: " + exception.getMessage(), currentElement);
        }
    }

    private void createSpecClass(ProtobufMessageElement result, PackageElement packageName) throws IOException {
        var simpleGeneratedClassName = result.getGeneratedClassNameBySuffix("Spec");
        var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;
        var sourceFile = processingEnv.getFiler().createSourceFile(qualifiedGeneratedClassName);
        try (var writer = new PrintWriter(sourceFile.openWriter())) {
            if(packageName != null) {
                writer.println("package %s;\n".formatted(packageName.getQualifiedName()));
            }

            var imports = getSpecImports(result);
            imports.forEach(entry -> writer.println("import %s;".formatted(entry)));
            if(!imports.isEmpty()){
                writer.println();
            }

            writer.println("public class %s {".formatted(simpleGeneratedClassName));
            var serializationVisitor = new ProtobufSerializationVisitor(result, writer);
            serializationVisitor.instrument();
            var deserializationVisitor = new ProtobufDeserializationVisitor(result, writer);
            deserializationVisitor.instrument();
            writer.println("}");
        }
    }

    protected List<String> getSpecImports(ProtobufMessageElement message) {
        if(message.isEnum()) {
            return List.of(
                    message.element().getQualifiedName().toString(),
                    Arrays.class.getName(),
                    Optional.class.getName()
            );
        }

        var imports = new ArrayList<String>();
        imports.add(message.element().getQualifiedName().toString());
        imports.add(ProtobufInputStream.class.getName());
        imports.add(ProtobufOutputStream.class.getName());
        if (message.properties().stream().anyMatch(ProtobufPropertyStub::required)) {
            imports.add(Objects.class.getName());
        }

        return Collections.unmodifiableList(imports);
    }

    private void createBuilderClass(ProtobufMessageElement messageElement, ProtobufBuilderElement builderElement, PackageElement packageName) throws IOException {
        var simpleGeneratedClassName = builderElement != null ? messageElement.getGeneratedClassNameByName(builderElement.name()) : messageElement.getGeneratedClassNameBySuffix("Builder");
        var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;
        var sourceFile = processingEnv.getFiler().createSourceFile(qualifiedGeneratedClassName);
        try (var writer = new PrintWriter(sourceFile.openWriter())) {
            if(packageName != null) {
                writer.println("package %s;\n".formatted(packageName.getQualifiedName()));
            }

            writer.println("public class %s {".formatted(simpleGeneratedClassName));
            var invocationArgs = new ArrayList<String>();
            if(builderElement != null) {
                for(var parameter : builderElement.parameters()) {
                    writer.println("    private %s %s;".formatted(parameter.asType(), parameter.getSimpleName()));
                    invocationArgs.add(parameter.getSimpleName().toString());
                }
            }else {
                for (var property : messageElement.properties()) {
                    writer.println("    private %s %s;".formatted(property.type().fieldType(), property.name()));
                    invocationArgs.add(property.name());
                }
            }
            writer.println();
            writer.println("    public %s() {".formatted(simpleGeneratedClassName));
            if(builderElement == null) {
                for (var property : messageElement.properties()) {
                    writer.println("        %s = %s;".formatted(property.name(), property.defaultValue()));
                }
            }

            writer.println("    }");
            writer.println();
            if(builderElement != null) {
                for(var parameter : builderElement.parameters()) {
                    writeBuilderSetter(writer, parameter.getSimpleName().toString(), parameter.asType(), simpleGeneratedClassName);
                }
            }else {
                for(var property : messageElement.properties()) {
                    writeBuilderSetter(writer, property.name(), property.type().fieldType(), simpleGeneratedClassName);
                }
            }
            writer.println();
            var resultQualifiedName = messageElement.element().getQualifiedName();
            var invocationArgsJoined = String.join(", ", invocationArgs);
            writer.println("    public %s build() {".formatted(resultQualifiedName));
            var invocation = builderElement == null ? "new %s(%s)".formatted(resultQualifiedName, invocationArgsJoined) : "%s.%s(%s)".formatted(resultQualifiedName, builderElement.delegate().getSimpleName(), invocationArgsJoined);
            writer.println("        return %s;".formatted(invocation));
            writer.println("    }");
            writer.println("}");
        }
    }

    private void writeBuilderSetter(PrintWriter writer, String fieldName, TypeMirror fieldType, String className) {
        writer.println("    public %s %s(%s %s) {".formatted(className, fieldName, fieldType, fieldName));
        writer.println("        this.%s = %s;".formatted(fieldName, fieldName));
        writer.println("        return this;");
        writer.println("    }");
        if (!(fieldType instanceof DeclaredType declaredType)) {
            return;
        }

        if(!(declaredType.asElement() instanceof TypeElement typeElement)) {
            return;
        }

        var optionalConverter = optionalDeserializers.get(typeElement.getQualifiedName().toString());
        if(optionalConverter != null) {
            var optionalValueType = getOptionalValueType(declaredType);
            if(optionalValueType.isEmpty()) {
                return;
            }

            writer.println("    public %s %s(%s %s) {".formatted(className, fieldName, optionalValueType.get(), fieldName));
            var converterWrapperClass = (TypeElement) optionalConverter.element().getEnclosingElement();
            writer.println("        this.%s = %s.%s(%s);".formatted(fieldName, converterWrapperClass.getQualifiedName(), optionalConverter.element().getSimpleName(), fieldName));
            writer.println("        return this;");
            writer.println("    }");
            return;
        }

        var atomicConverter = atomicDeserializers.get(typeElement.getQualifiedName().toString());
        if(atomicConverter != null) {
            var atomicValueType = getAtomicValueType(declaredType);
            if(atomicValueType.isEmpty()) {
                return;
            }

            writer.println("    public %s %s(%s %s) {".formatted(className, fieldName, atomicValueType.get(), fieldName));
            var converterWrapperClass = (TypeElement) atomicConverter.element().getEnclosingElement();
            writer.println("        this.%s = new %s(%s);".formatted(fieldName, converterWrapperClass.getQualifiedName(), fieldName));
            writer.println("        return this;");
            writer.println("    }");
        }
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
            printWarning("No properties found", message);
            return Optional.of(messageElement);
        }


        if (!hasPropertiesConstructor(messageElement)) {
            printError("Missing protobuf constructor: a protobuf message must provide a constructor that takes only its properties, following their declaration order, as parameters", message);
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
                        if(!isSubType(property.type().fieldType(), constructorParameter.asType())) {
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

        var accessor = getAccessor(variableElement, propertyAnnotation).orElse(null);
        if(accessor == null) {
            printError("Missing getter/accessor: a getter or accessor must be declared", variableElement);
            return;
        }

        if(accessor.getModifiers().contains(Modifier.PRIVATE)) {
            printError("Weak visibility: the getter/accessor must have at least package-private visibility", accessor);
            return;
        }

        var type = getImplementationType(variableElement, variableElement.asType(), accessor, propertyAnnotation);
        if(type.isEmpty()) {
            return;
        }

        var error = messageElement.addProperty(variableElement, accessor, type.get(), propertyAnnotation);
        if(error.isEmpty()) {
            return;
        }

        printError("Duplicated message property: %s and %s with index %s".formatted(variableElement.getSimpleName(), error.get().name(), propertyAnnotation.index()), variableElement);
    }

    private Optional<ProtobufPropertyType> getImplementationType(Element element, TypeMirror elementType, Element accessor, ProtobufProperty property) {
        var mixin = getMirroredMixin(property).orElse(null);
        var override = getMirroredOverride(property);
        if (isSubType(elementType, collectionType)) {
            var collectionTypeParameter = getTypeParameter(elementType, collectionType, 0);
            if (collectionTypeParameter.isEmpty()) {
                printError("Type inference error: cannot determine collection's implementationType. Specify the implementation explicitly in @ProtobufProperty", element);
                return Optional.empty();
            }

            var concreteCollectionType = getConcreteCollectionType(elementType);
            if(concreteCollectionType.isEmpty()) {
                printError("Type inference error: no known implementation found for abstract implementationType. Use a concrete type for the property's field to fix this error", element);
                return Optional.empty();
            }

            var value = new ProtobufPropertyType.NormalType(property.type(), collectionTypeParameter.get(), override.orElse(collectionTypeParameter.get()), isEnum(collectionTypeParameter.get()));
            var type = new ProtobufPropertyType.CollectionType(elementType, concreteCollectionType.get(), value);
            attributeConverters(element, property.type(), collectionTypeParameter.get(), value, mixin);
            return Optional.of(type);
        }

        if(isSubType(elementType, mapType)) {
            var implementation = getConcreteMapImplementation(element, elementType, property.keyType(), property.valueType());
            if (implementation.isEmpty()) {
                printError("Type inference error: no known implementation found for abstract implementationType. Use a concrete implementationType for the property's field to fix this error", element);
                return Optional.empty();
            }

            attributeConverters(element, implementation.get().keyType().protobufType(), implementation.get().keyType().fieldType(), implementation.get().keyType(), mixin);
            attributeConverters(element, implementation.get().valueType().protobufType(), implementation.get().valueType().fieldType(), implementation.get().valueType(), mixin);
            return Optional.of(implementation.get());
        }

        var rawAccessorType = switch (accessor) {
            case ExecutableElement executableElement -> erase(executableElement.getReturnType());
            case VariableElement variableElement -> erase(variableElement.asType());
            default -> throw new IllegalStateException("Unexpected value: " + accessor);
        };
        var rawFieldType = erase(elementType);
        var optionalDeserializer = optionalDeserializers.get(rawFieldType.toString());
        if (optionalDeserializer != null) {
            var declaredFieldType = (DeclaredType) elementType;
            var wrappedType = getOptionalValueType(declaredFieldType);
            if (wrappedType.isEmpty()) {
                return Optional.empty();
            }

            var rawWrappedType = erase(wrappedType.get());
            var value = new ProtobufPropertyType.NormalType(property.type(), wrappedType.get(), override.orElse(wrappedType.get()), isEnum(wrappedType.get()));
            var implementation = new ProtobufPropertyType.OptionalType(elementType, value);
            value.addNullableConverter(optionalSerializers.get(rawAccessorType.toString()));
            attributeConverters(element, property.type(), rawWrappedType, value, mixin);
            value.addNullableConverter(optionalDeserializer);
            return Optional.of(implementation);
        }

        var atomicDeserializer = atomicDeserializers.get(rawFieldType.toString());
        if (atomicDeserializer != null) {
            var declaredFieldType = (DeclaredType) elementType;
            var wrappedType = getAtomicValueType(declaredFieldType);
            if (wrappedType.isEmpty()) {
                return Optional.empty();
            }

            var rawWrappedType = erase(wrappedType.get());
            var value = new ProtobufPropertyType.NormalType(property.type(), wrappedType.get(), override.orElse(wrappedType.get()), isEnum(wrappedType.get()));
            var implementation = new ProtobufPropertyType.AtomicType(elementType, value);
            value.addNullableConverter(atomicSerializers.get(rawAccessorType.toString()));
            attributeConverters(element, property.type(), rawWrappedType, value, mixin);
            value.addNullableConverter(atomicDeserializer);
            return Optional.of(implementation);
        }

        var implementation = new ProtobufPropertyType.NormalType(property.type(), elementType, override.orElse(elementType), isEnum(rawFieldType));
        implementation.addNullableConverter(optionalSerializers.get(rawAccessorType.toString()));
        implementation.addNullableConverter(atomicSerializers.get(rawAccessorType.toString()));
        attributeConverters(element, property.type(), elementType, implementation, mixin);
        return Optional.of(implementation);
    }

    private Optional<Element> getMirroredMixin(ProtobufProperty property) {
        try {
            var implementation = property.mixin();
            var type = processingEnv.getElementUtils().getTypeElement(implementation.getName());
            if(type == null || isSameType(type.asType(), protoMixinType)) {
                return Optional.empty();
            }

            return Optional.of(type);
        }catch (MirroredTypeException exception) {
            if (!(exception.getTypeMirror() instanceof DeclaredType declaredType)) {
                return Optional.empty();
            }

            if(isSameType(declaredType, protoMixinType)) {
                return Optional.empty();
            }

            return Optional.of(declaredType.asElement());
        }
    }

    private Optional<TypeMirror> getMirroredOverride(ProtobufProperty property) {
        try {
            var implementation = property.overrideType();
            var type = processingEnv.getElementUtils().getTypeElement(implementation.getName());
            if(type == null || isSameType(type.asType(), objectType)) {
                return Optional.empty();
            }

            return Optional.of(type.asType());
        }catch (MirroredTypeException exception) {
            if (!(exception.getTypeMirror() instanceof DeclaredType declaredType)) {
                return Optional.empty();
            }

            if(isSameType(declaredType, objectType)) {
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
        if(isSameType(elementType, collectionType) || isSameType(elementType, listType)) {
            return Optional.ofNullable(processingEnv.getTypeUtils().getDeclaredType(arrayListTypeElement, typeArguments));
        }else if(isSameType(elementType, setType)) {
            return Optional.ofNullable(processingEnv.getTypeUtils().getDeclaredType(hashSetTypeElement, typeArguments));
        }else if(isSameType(elementType, queueType) || isSameType(elementType, dequeType)) {
            return Optional.ofNullable(processingEnv.getTypeUtils().getDeclaredType(linkedListTypeElement, typeArguments));
        }else {
            return Optional.empty();
        }
    }

    private Optional<ProtobufPropertyType.MapType> getConcreteMapImplementation(Element element, TypeMirror elementType, ProtobufType keyType, ProtobufType valueType) {
        if(!(elementType instanceof DeclaredType declaredFieldType)) {
            return Optional.empty();
        }

        var error = false;
        var keyTypeParameter = getTypeParameter(elementType, mapType, 0);
        if (keyTypeParameter.isEmpty()) {
            printError("Type inference error: cannot determine map's key implementationType. Specify the implementation explicitly in @ProtobufProperty", element);
            error = true;
        }

        if(keyType == ProtobufType.MAP) {
            printError("Missing implementationType error: specify the implementationType of the map's key in @ProtobufProperty", element);
            error = true;
        }

        if(valueType == ProtobufType.MAP) {
            printError("Missing implementationType error: specify the implementationType of the map's value in @ProtobufProperty", element);
            error = true;
        }

        if(keyType == ProtobufType.OBJECT) {
            printError("Type error: unsupported map key implementationType", element);
            error = true;
        }

        if(error) {
            return Optional.empty();
        }

        var keyEntry = new ProtobufPropertyType.NormalType(keyType, keyTypeParameter.get(), keyTypeParameter.get(), false);
        var valueTypeParameter = getTypeParameter(elementType, mapType, 1);
        if (valueTypeParameter.isEmpty()) {
            printError("Type inference error: cannot determine map's value implementationType. Specify the implementation explicitly in @ProtobufProperty", element);
            return Optional.empty();
        }

        var valueEntry = new ProtobufPropertyType.NormalType(valueType, valueTypeParameter.get(), valueTypeParameter.get(), isEnum(valueTypeParameter.get()));
        var fieldTypeElement = (TypeElement) declaredFieldType.asElement();
        if(!fieldTypeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return Optional.of(new ProtobufPropertyType.MapType(elementType, elementType, keyEntry, valueEntry));
        }

        var rawFieldType = erase(elementType);
        var typeArguments = declaredFieldType.getTypeArguments() != null ? declaredFieldType.getTypeArguments().toArray(TypeMirror[]::new) : new TypeMirror[0];
        if(isSameType(rawFieldType, mapType)) {
            var hashMapType = processingEnv.getTypeUtils().getDeclaredType(hashMapElement, typeArguments);
            return Optional.of(new ProtobufPropertyType.MapType(elementType, hashMapType, keyEntry, valueEntry));
        }else if(isSameType(rawFieldType, concurrentMapType)) {
            var concurrentHashMapType = processingEnv.getTypeUtils().getDeclaredType(concurrentHashMapElement, typeArguments);
            return Optional.of(new ProtobufPropertyType.MapType(elementType, concurrentHashMapType, keyEntry, valueEntry));
        } else {
            return Optional.empty();
        }
    }

    private Optional<TypeMirror> getAtomicValueType(DeclaredType declaredFieldType) {
        return switch (declaredFieldType.asElement().getSimpleName().toString()) {
            case "AtomicReference" -> declaredFieldType.getTypeArguments().isEmpty() ? Optional.empty() : Optional.of(declaredFieldType.getTypeArguments().getFirst());
            case "AtomicInteger" -> Optional.of(getType(Integer.class));
            case "AtomicLong" -> Optional.of(getType(Long.class));
            case "AtomicBoolean" -> Optional.of(getType(Boolean.class));
            default -> Optional.empty();
        };
    }

    private Optional<TypeMirror> getOptionalValueType(DeclaredType declaredFieldType) {
        return switch (declaredFieldType.asElement().getSimpleName().toString()) {
            case "Optional" ->
                    declaredFieldType.getTypeArguments().isEmpty() ? Optional.empty() : Optional.of(declaredFieldType.getTypeArguments().getFirst());
            case "OptionalLong" -> Optional.of(getType(Long.class));
            case "OptionalInt" -> Optional.of(getType(Integer.class));
            case "OptionalDouble" -> Optional.of(getType(Double.class));
            default -> Optional.empty();
        };
    }

    private void attributeConverters(Element invoker, ProtobufType from, TypeMirror to, ProtobufPropertyType implementation, Element mixin) {
        var fromType = getType(from.wrappedType());
        if(!(to instanceof DeclaredType declaredType)) {
            return;
        }

        if(!isSubType(to, fromType) || (from == ProtobufType.OBJECT && !isSubType(to, protoMessageType) && !isSubType(to, protoEnumType))) {
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

                if (isDeserializer(element, fromType)) {
                    deserializer = element;
                } else if (isSerializer(element, to, fromType)) {
                    serializer = element;
                }
            }

            if (serializer != null) {
                implementation.addNullableConverter(new ProtobufSerializerElement(serializer, serializer.getReturnType().getKind().isPrimitive(), isOptional(serializer)));
            } else {
                printError("Missing converter: cannot find a serializer for %s".formatted(to), invoker);
            }

            if (deserializer != null) {
                implementation.addNullableConverter(new ProtobufDeserializerElement(deserializer));
            } else {
                printError("Missing converter: cannot find a deserializer for %s".formatted(fromType), invoker);
            }
        }
    }

    private boolean isOptional(ExecutableElement serializer) {
       return isOptional(serializer.getReturnType());
    }

    private boolean isOptional(TypeMirror typeMirror) {
        var erased = erase(typeMirror);
        return optionalSerializers.get(erased.toString()) != null;
    }

    private boolean isDeserializer(ExecutableElement entry, TypeMirror from) {
        return entry.getAnnotation(ProtobufConverter.class) != null
                && entry.getModifiers().contains(Modifier.STATIC)
                && entry.getParameters().size() == 1
                && isSubType(from, entry.getParameters().getFirst().asType());
    }

    private boolean isSerializer(ExecutableElement entry, TypeMirror to, TypeMirror from) {
        var isStatic = entry.getModifiers().contains(Modifier.STATIC);
        return entry.getAnnotation(ProtobufConverter.class) != null
                && entry.getParameters().size() == (isStatic ? 1 : 0)
                && (!isStatic || isSubType(to, entry.getParameters().getFirst().asType()))
                && isSubType(from, entry.getReturnType());
    }

    private boolean isEnum(TypeMirror mirror) {
        return mirror instanceof DeclaredType declaredType
                && declaredType.asElement().getKind() == ElementKind.ENUM;
    }

    private Optional<TypeMirror> getTypeParameter(TypeMirror mirror, TypeMirror targetType, int index) {
        if(!(mirror instanceof DeclaredType declaredType)) {
            return Optional.empty();
        }

        if (isSameType(mirror, targetType)) {
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
        if (isSameType(implemented, targetType)) {
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

    private boolean isSameType(TypeMirror firstType, TypeMirror secondType) {
        return processingEnv.getTypeUtils().isSameType(erase(firstType), secondType);
    }

    private TypeMirror erase(TypeMirror typeMirror) {
        var result = processingEnv.getTypeUtils().erasure(typeMirror);
        return result == null ? typeMirror : result;
    }

    private boolean isSubType(TypeMirror child, TypeMirror parent) {
        if(child instanceof PrimitiveType primitiveType) {
            var boxed = processingEnv.getTypeUtils().boxedClass(primitiveType);
            child = boxed.asType();
        }

        if(parent instanceof PrimitiveType primitiveType) {
            var boxed = processingEnv.getTypeUtils().boxedClass(primitiveType);
            parent = boxed.asType();
        }

        return processingEnv.getTypeUtils().isSubtype(erase(child), erase(parent));
    }

    private boolean isValidRequiredProperty(VariableElement variableElement) {
        if(variableElement.asType().getKind().isPrimitive()) {
            printError("Required properties cannot be primitives", variableElement);
            return false;
        }

        return true;
    }

    private boolean isValidPackedProperty(VariableElement variableElement, ProtobufProperty propertyAnnotation) {
        if(!propertyAnnotation.packed() || isSubType(variableElement.asType(), collectionType)) {
            return true;
        }

        printError("Only scalar properties can be packed", variableElement);
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

        printWarning("No constants found", enumElement);
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
            printError("Missing protobuf enum constructor: an enum should provide a constructor with a scalar parameter annotated with @ProtobufEnumIndex", enumElement);
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
                    printError("Duplicated protobuf constructor: an enum should provide only one constructor with a scalar parameter annotated with @ProtobufEnumIndex", second.constructor());
                    return first;
                });
    }

    private Optional<ProtobufEnumMetadata> getEnumMetadata(ExecutableElement constructor, ProtobufEnumFields fields) {
        var constructorTree = trees.getTree(constructor);
        return IntStream.range(0, constructor.getParameters().size())
                .filter(index -> isImplicitEnumConstructor(constructor) || hasProtobufIndexAnnotation(constructor, index))
                .mapToObj(index -> getEnumMetadata(constructor, constructor.getParameters().get(index), index, constructorTree, fields))
                .reduce((first, second) -> {
                    printError("Duplicated protobuf enum index: an enum constructor should provide only one parameter annotated with @ProtobufEnumIndex", second.parameter());
                    return first;
                });
    }

    private boolean isImplicitEnumConstructor(ExecutableElement constructor) {
        return constructor.getParameters().size() == 1
                && isSameType(constructor.getParameters().getFirst().asType(), intType);
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
                    printError("Duplicated assignment: the parameter annotated with @ProtobufEnumIndex must be assigned to a single local field", second);
                    return first;
                })
                .map(fieldElement -> {
                    checkProtobufEnumIndexField(fieldElement);
                    return new ProtobufEnumMetadata(constructor, fieldElement, parameter, index);
                })
                .orElseGet(() -> {
                    printError("Missing or too complex assignment: the parameter annotated with @ProtobufEnumIndex should be assigned to a local field", constructor);
                    printError("If the assignment is too complex for the compiler to evaluate, annotate the local field directly with @ProtobufEnumIndex", constructor);
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

        printError("Weak visibility: the field annotated with @ProtobufEnumIndex must have at least package-private visibility", variableElement);
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
            printError("%s doesn't specify an index".formatted(variableName), enumElement);
            return;
        }

        var indexArgument = newClassTree.getArguments().get(messageElement.enumMetadata().orElseThrow().parameterIndex());
        if (!(indexArgument instanceof LiteralTree literalTree)) {
            printError("%s's index must be a constant value".formatted(variableName), enumElement);
            return;
        }

        var value = ((Number) literalTree.getValue()).intValue();
        if (value < 0) {
            printError("%s's index must be a positive".formatted(variableName), enumElement);
            return;
        }

        var error = messageElement.addConstant(value, variableName);
        if(error.isEmpty()) {
            return;
        }

        printError("Duplicated enum constant: %s and %s with index %s".formatted(variableName, error.get(), value), enumElement);
    }

    private List<TypeElement> getProtobufObjects(RoundEnvironment roundEnv) {
        return getElements(roundEnv.getRootElements())
                .stream()
                .filter(entry -> isSubType(entry.asType(), protoObjectType))
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

    private void printWarning(String msg, Element constructor) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, constructor);
    }

    private void printError(String msg, Element constructor) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, constructor);
    }
}
