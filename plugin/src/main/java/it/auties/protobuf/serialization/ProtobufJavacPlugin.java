package it.auties.protobuf.serialization;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.ProtobufBuilder;
import it.auties.protobuf.annotation.ProtobufConverter;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.extension.OptionalExtension;
import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufObject;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.instrumentation.ProtobufDeserializationVisitor;
import it.auties.protobuf.serialization.instrumentation.ProtobufSerializationVisitor;
import it.auties.protobuf.serialization.model.*;
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
    private DeclaredType objectType;
    private DeclaredType protoObjectType;
    private DeclaredType protoMessageType;
    private DeclaredType protoEnumType;
    private DeclaredType collectionType;
    private DeclaredType listType;
    private TypeElement arrayListTypeElement;
    private DeclaredType setType;
    private TypeElement hashSetTypeElement;
    private DeclaredType queueType;
    private DeclaredType dequeType;
    private TypeElement linkedListTypeElement;
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
        this.objectType = (DeclaredType) getType(Object.class);
        this.protoObjectType = (DeclaredType) getType(ProtobufObject.class);
        this.protoMessageType = (DeclaredType) getType(ProtobufMessage.class);
        this.protoEnumType = (DeclaredType) getType(ProtobufEnum.class);
        this.intType = (PrimitiveType) getType(int.class);
        this.collectionType = (DeclaredType) erase(getType(Collection.class));
        this.listType = (DeclaredType) erase(getType(List.class));
        var arrayListType = (DeclaredType) erase(getType(ArrayList.class));
        this.arrayListTypeElement = (TypeElement) arrayListType.asElement();
        this.setType = (DeclaredType) erase(getType(Set.class));
        var hashSetType = (DeclaredType) erase(getType(HashSet.class));
        this.arrayListTypeElement = (TypeElement) arrayListType.asElement();
        this.hashSetTypeElement = (TypeElement) hashSetType.asElement();
        this.queueType = (DeclaredType) erase(getType(Queue.class));
        this.dequeType = (DeclaredType) erase(getType(Deque.class));
        var linkedListType = (DeclaredType) erase(getType(LinkedList.class));
        this.linkedListTypeElement = (TypeElement) linkedListType.asElement();
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
                new ProtobufSerializerElement(atomicReferenceSerializer),
                AtomicInteger.class.getName(),
                new ProtobufSerializerElement(atomicIntegerSerializer),
                AtomicLong.class.getName(),
                new ProtobufSerializerElement(atomicLongSerializer),
                AtomicBoolean.class.getName(),
                new ProtobufSerializerElement(atomicBooleanSerializer)
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
                new ProtobufSerializerElement(optionalSerializer, "null"),
                OptionalInt.class.getName(),
                new ProtobufSerializerElement(optionalIntSerializer),
                OptionalLong.class.getName(),
                new ProtobufSerializerElement(optionalLongSerializer),
                OptionalDouble.class.getName(),
                new ProtobufSerializerElement(optionalDoubleSerializer)
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
            List<ProtobufPropertyStub> properties = messageElement.properties();
            if(builderElement != null) {
                for(var parameter : builderElement.parameters()) {
                    writer.println("    private %s %s;".formatted(parameter.asType(), parameter.getSimpleName()));
                    invocationArgs.add(parameter.getSimpleName().toString());
                }
            }else {
                for (var property : properties) {
                    writer.println("    private %s %s;".formatted(property.type().fieldType(), property.name()));
                    invocationArgs.add(property.name());
                }
            }
            writer.println();
            writer.println("    public %s() {".formatted(simpleGeneratedClassName));
            writer.println("    }");
            writer.println();
            if(builderElement != null) {
                for(var parameter : builderElement.parameters()) {
                    writeBuilderSetter(writer, parameter.getSimpleName().toString(), parameter.asType(), simpleGeneratedClassName);
                }
            }else {
                for(var property : properties) {
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
        processMessage(messageElement);
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


    private void processMessage(ProtobufMessageElement messageElement) {
        for (var entry : messageElement.element().getEnclosedElements()) {
            if (entry instanceof VariableElement variableElement) {
                processProperty(messageElement, variableElement);
            }else if(entry instanceof ExecutableElement executableElement) {
                var builder = executableElement.getAnnotation(ProtobufBuilder.class);
                if(builder != null) {
                    messageElement.addBuilder(builder.className(), executableElement.getParameters(), executableElement);
                }
            }
        }
    }

    private boolean hasPropertiesConstructor(ProtobufMessageElement message) {
        return message.element().getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof ExecutableElement)
                .map(entry -> (ExecutableElement) entry)
                .filter(entry -> entry.getKind() == ElementKind.CONSTRUCTOR)
                .anyMatch(constructor -> {
                    var constructorParameters = constructor.getParameters();
                    if(message.properties().size() != constructorParameters.size()) {
                        return false;
                    }

                    // No var args support as it's needed
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

        if(propertyAnnotation.repeated() && !checkRepeatedField(variableElement)) {
            return;
        }


        var accessor = getAccessor(variableElement).orElse(null);
        if(accessor == null) {
            printError("Missing getter/accessor: a getter or accessor must be declared", variableElement);
            return;
        }

        if(accessor.getModifiers().contains(Modifier.PRIVATE)) {
            printError("Weak visibility: the getter/accessor must have at least package-private visibility", accessor);
            return;
        }

        var type = getImplementationType(variableElement, accessor, propertyAnnotation);
        if(type.isEmpty()) {
            return;
        }

        var error = messageElement.addProperty(variableElement, accessor, type.get(), propertyAnnotation);
        if(error.isEmpty()) {
            return;
        }

        printError("Duplicated message property: %s and %s with index %s".formatted(variableElement.getSimpleName(), error.get().name(), propertyAnnotation.index()), variableElement);
    }

    private Optional<ProtobufPropertyType> getImplementationType(VariableElement field, ExecutableElement accessor, ProtobufProperty property) {
        var implementationAstType = getExplicitImplementationType(field, property);
        if (property.repeated()) {
            var collectionTypeParameter = getCollectionTypeParameter(field.asType())
                    .orElse(null);
            if (collectionTypeParameter == null) {
                printError("Type inference error: cannot determine collection's type. Specify the implementation explicitly in @ProtobufProperty", field);
                return Optional.empty();
            }

            var concreteCollectionType = getConcreteCollectionType(field)
                    .orElse(null);
            if(concreteCollectionType == null) {
                printError("Type inference error: no known implementation found for abstract type. Use a concrete type for the property's field to fix this error", field);
                return Optional.empty();
            }

            var actualCollectionTypeParameter = implementationAstType.orElse(collectionTypeParameter);
            var result = new ProtobufPropertyType(property.type(), field.asType(), actualCollectionTypeParameter, concreteCollectionType, isEnum(actualCollectionTypeParameter));
            var converters = getPropertyConverters(field, implementationAstType.orElse(null), actualCollectionTypeParameter, property.type());
            converters.forEach(result::addNullableConverter);
            return Optional.of(result);
        }

        var rawAccessorType = erase(accessor.getReturnType());
        var rawFieldType = erase(field.asType());
        var optionalDeserializer = optionalDeserializers.get(rawFieldType.toString());
        if (optionalDeserializer != null) {
            var declaredFieldType = (DeclaredType) field.asType();
            var wrappedType = getOptionalValueType(declaredFieldType);
            if (wrappedType.isEmpty()) {
                return Optional.empty();
            }

            var rawWrappedType = erase(wrappedType.get());
            var result = new ProtobufPropertyType(property.type(), field.asType(), implementationAstType.orElse(rawWrappedType), null, isEnum(rawWrappedType));
            result.addNullableConverter(optionalSerializers.get(rawAccessorType.toString()));
            var converters = getPropertyConverters(field, implementationAstType.orElse(null), rawWrappedType, property.type());
            converters.forEach(result::addNullableConverter);
            result.addNullableConverter(optionalDeserializer);
            return Optional.of(result);
        }

        var atomicDeserializer = atomicDeserializers.get(rawFieldType.toString());
        if (atomicDeserializer != null) {
            var declaredFieldType = (DeclaredType) field.asType();
            var wrappedType = getAtomicValueType(declaredFieldType);
            if (wrappedType.isEmpty()) {
                return Optional.empty();
            }

            var rawWrappedType = erase(wrappedType.get());
            var result = new ProtobufPropertyType(property.type(), field.asType(), implementationAstType.orElse(rawWrappedType), null, isEnum(rawWrappedType));
            result.addNullableConverter(atomicSerializers.get(rawAccessorType.toString()));
            var converters = getPropertyConverters(field, implementationAstType.orElse(null), rawWrappedType, property.type());
            converters.forEach(result::addNullableConverter);
            result.addNullableConverter(atomicDeserializer);
            return Optional.of(result);
        }

        var result = new ProtobufPropertyType(property.type(), field.asType(), implementationAstType.orElse(field.asType()), null, isEnum(rawFieldType));
        result.addNullableConverter(optionalSerializers.get(rawAccessorType.toString()));
        result.addNullableConverter(atomicSerializers.get(rawAccessorType.toString()));
        var converters = getPropertyConverters(field, implementationAstType.orElse(null), field.asType(), property.type());
        converters.forEach(result::addNullableConverter);
        return Optional.of(result);
    }

    private static Optional<ExecutableElement> getAccessor(VariableElement fieldElement) {
        var fieldName = fieldElement.getSimpleName().toString();
        return fieldElement.getEnclosingElement()
                .getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof ExecutableElement)
                .map(entry -> (ExecutableElement) entry)
                .filter(entry -> {
                    var methodName = entry.getSimpleName().toString();
                    return entry.getParameters().isEmpty()
                            && (methodName.equalsIgnoreCase("get" + fieldName) || methodName.equalsIgnoreCase(fieldName));
                })
                .findFirst();
    }

    private Optional<TypeMirror> getConcreteCollectionType(VariableElement fieldElement) {
        if(!(fieldElement.asType() instanceof DeclaredType declaredFieldType)) {
            return Optional.empty();
        }


        var fieldTypeElement = (TypeElement) declaredFieldType.asElement();
        if(!fieldTypeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return Optional.of(fieldElement.asType());
        }

        var rawFieldType = erase(fieldElement.asType());
        var typeArguments = declaredFieldType.getTypeArguments() != null ? declaredFieldType.getTypeArguments().toArray(TypeMirror[]::new) : new TypeMirror[0];
        if(isSameType(rawFieldType, collectionType) || isSameType(rawFieldType, listType)) {
            return Optional.ofNullable(processingEnv.getTypeUtils().getDeclaredType(arrayListTypeElement, typeArguments));
        }else if(isSameType(rawFieldType, setType)) {
            return Optional.ofNullable(processingEnv.getTypeUtils().getDeclaredType(hashSetTypeElement, typeArguments));
        }else if(isSameType(rawFieldType, queueType) || isSameType(rawFieldType, dequeType)) {
            return Optional.ofNullable(processingEnv.getTypeUtils().getDeclaredType(linkedListTypeElement, typeArguments));
        }else {
            return Optional.empty();
        }
    }

    private Optional<TypeMirror> getAtomicValueType(DeclaredType declaredFieldType) {
        return switch (declaredFieldType.asElement().getSimpleName().toString()) {
            case "AtomicReference" -> declaredFieldType.getTypeArguments().isEmpty() ? Optional.empty() : Optional.of(declaredFieldType.getTypeArguments().get(0));
            case "AtomicInteger" -> Optional.of(getType(Integer.class));
            case "AtomicLong" -> Optional.of(getType(Long.class));
            case "AtomicBoolean" -> Optional.of(getType(Boolean.class));
            default -> Optional.empty();
        };
    }

    private Optional<TypeMirror> getOptionalValueType(DeclaredType declaredFieldType) {
        return switch (declaredFieldType.asElement().getSimpleName().toString()) {
            case "Optional" ->
                    declaredFieldType.getTypeArguments().isEmpty() ? Optional.empty() : Optional.of(declaredFieldType.getTypeArguments().get(0));
            case "OptionalLong" -> Optional.of(getType(Long.class));
            case "OptionalInt" -> Optional.of(getType(Integer.class));
            case "OptionalDouble" -> Optional.of(getType(Double.class));
            default -> Optional.empty();
        };
    }

    private List<ProtobufConverterElement> getPropertyConverters(VariableElement invoker, TypeMirror implementation, TypeMirror fieldType, ProtobufType protobufType) {
        if (implementation == null) {
            var wrapperType = getType(protobufType.wrappedType());
            return isSubType(fieldType, wrapperType) ? List.of() : getConvertersForType(invoker, fieldType, wrapperType);

        }

        return isSubType(fieldType, implementation) ? List.of() : getConvertersForType(invoker, implementation, fieldType);
    }

    private List<ProtobufConverterElement> getConvertersForType(VariableElement invoker, TypeMirror to, TypeMirror from) {
        if (!(to instanceof DeclaredType declaredType)) {
            return List.of();
        }

        ExecutableElement serializer = null;
        ExecutableElement deserializer = null;
        for (var entry : declaredType.asElement().getEnclosedElements()) {
            if(serializer != null && deserializer != null) {
                break;
            }

            if (!(entry instanceof ExecutableElement element)) {
                continue;
            }

            if (isDeserializer(element, from)) {
                deserializer = element;
            }else if(isSerializer(element, from)){
                serializer = element;
            }
        }

        var results = new ArrayList<ProtobufConverterElement>();
        if(serializer != null) {
            results.add(new ProtobufSerializerElement(serializer));
        }else {
            printError("Missing converter: cannot find a element for %s".formatted(from), invoker);
        }

        if(deserializer != null) {
            results.add(new ProtobufDeserializerElement(deserializer));
        }else {
            printError("Missing converter: cannot find a deserializer for %s".formatted(from), invoker);
        }

        return Collections.unmodifiableList(results);
    }

    private boolean isDeserializer(ExecutableElement entry, TypeMirror fieldAstType) {
        return entry.getAnnotation(ProtobufConverter.class) != null
                && entry.getParameters().size() == 1
                && isSubType(fieldAstType, entry.getParameters().get(0).asType());
    }

    private boolean isSerializer(ExecutableElement entry, TypeMirror fieldAstType) {
        return entry.getAnnotation(ProtobufConverter.class) != null
                && entry.getParameters().isEmpty()
                && isSubType(fieldAstType, entry.getReturnType());
    }

    private Optional<TypeMirror> getExplicitImplementationType(VariableElement element, ProtobufProperty property) {
        var rawImplementation = getMirroredImplementation(property);
        if(rawImplementation.isEmpty()) {
            return Optional.empty();
        }

        if (isSameType(rawImplementation.get(), objectType)) {
            return Optional.empty();
        }

        if(rawImplementation.get().asElement().getModifiers().contains(Modifier.ABSTRACT)) {
            printError("Type error: a concrete implementation is required", element);
            return Optional.empty();
        }

        return Optional.of(rawImplementation.get());
    }

    private Optional<DeclaredType> getMirroredImplementation(ProtobufProperty property) {
        try {
            var implementation = property.implementation();
            throw new IllegalArgumentException(implementation.toString());
        }catch (MirroredTypeException exception) {
            if(exception.getTypeMirror() instanceof DeclaredType declaredType) {
                return Optional.of(declaredType);
            }

            return Optional.empty();
        }
    }

    private boolean isEnum(TypeMirror mirror) {
        return mirror instanceof DeclaredType declaredType
                && declaredType.asElement().getKind() == ElementKind.ENUM;
    }

    private Optional<TypeMirror> getCollectionTypeParameter(TypeMirror mirror) {
        if(!(mirror instanceof DeclaredType declaredType)) {
            return Optional.empty();
        }

        var typeElement = (TypeElement) declaredType.asElement();
        return typeElement.getInterfaces()
                .stream()
                .filter(implemented -> implemented instanceof DeclaredType)
                .map(implemented -> (DeclaredType) implemented)
                .map(implemented -> getCollectionTypeByImplement(declaredType, implemented))
                .flatMap(Optional::stream)
                .findFirst()
                .or(() -> getCollectionTypeBySuperClass(declaredType, typeElement));
    }

    private Optional<TypeMirror> getCollectionTypeByImplement(DeclaredType declaredType, DeclaredType implemented) {
        if (isSameType(implemented, collectionType)) {
            var collectionTypeArgument = implemented.getTypeArguments().get(0);
            return getConcreteCollectionArgumentType(collectionTypeArgument, declaredType);
        }

        return getCollectionTypeParameter(implemented)
                .flatMap(result -> getConcreteCollectionArgumentType(result, declaredType));
    }

    private Optional<TypeMirror> getCollectionTypeBySuperClass(DeclaredType declaredType, TypeElement typeElement) {
        if (!(typeElement.getSuperclass() instanceof DeclaredType superDeclaredType)) {
            return Optional.empty();
        }

        return getCollectionTypeParameter(superDeclaredType)
                .flatMap(result -> getConcreteCollectionArgumentType(result, superDeclaredType))
                .flatMap(result -> getConcreteCollectionArgumentType(result, declaredType));
    }

    private Optional<TypeMirror> getConcreteCollectionArgumentType(TypeMirror argumentMirror, DeclaredType previousType) {
        if(argumentMirror instanceof DeclaredType declaredTypeArgument) {
            return Optional.of(declaredTypeArgument);
        }else if(argumentMirror instanceof TypeVariable typeVariableArgument){
            return getConcreteTypeFromTypeVariable(typeVariableArgument, previousType);
        }else {
            return Optional.empty();
        }
    }

    private Optional<TypeMirror> getConcreteTypeFromTypeVariable(TypeVariable typeVariableArgument, DeclaredType previousType) {
        var currentTypeVarName = typeVariableArgument.asElement().getSimpleName();
        var previousTypeArguments = previousType.getTypeArguments();
        var previousElement = (TypeElement) previousType.asElement();
        var previousTypeParameters = previousElement.getTypeParameters();
        for(var i = 0; i < previousTypeParameters.size(); i++) {
            if(previousTypeParameters.get(i).getSimpleName().equals(currentTypeVarName)){
                return Optional.of(previousTypeArguments.get(i));
            }
        }
        return Optional.empty();
    }

    private boolean isSameType(TypeMirror firstType, TypeMirror secondType) {
        return processingEnv.getTypeUtils().isSameType(erase(firstType), secondType);
    }

    private TypeMirror erase(TypeMirror typeMirror) {
        if(!(typeMirror instanceof DeclaredType declaredType)) {
            return typeMirror;
        }

        return processingEnv.getTypeUtils().erasure(declaredType);
    }

    private boolean checkRepeatedField(VariableElement variableElement) {
        if (isSubType(variableElement.asType(), collectionType)) {
            return true;
        }

        printError("Type mismatch: the type of a repeated field must extend java.lang.Collection", variableElement);
        return false;
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
        if(!propertyAnnotation.repeated()) {
            printError("Only repeated properties can be packed", variableElement);
            return false;
        }

        if(!propertyAnnotation.type().isPackable()) {
            printError("Only scalar properties can be packed", variableElement);
            return false;
        }

        return true;
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
                && isSameType(constructor.getParameters().get(0).asType(), intType);
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
