package it.auties.protobuf.serialization;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.ProtobufConverter;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufObject;
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
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.IntStream;

@SupportedAnnotationTypes({
        "it.auties.protobuf.annotation.ProtobufProperty",
        "it.auties.protobuf.annotation.ProtobufEnumIndex"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ProtobufJavacPlugin extends AbstractProcessor {
    private Trees trees;
    private TypeMirror objectType;
    private TypeMirror protoObjectType;
    private TypeMirror protoMessageType;
    private TypeMirror protoEnumType;
    private TypeMirror collectionType;

    @Override
    public synchronized void init(ProcessingEnvironment wrapperProcessingEnv) {
        var unwrappedProcessingEnv = unwrapProcessingEnv(wrapperProcessingEnv);
        super.init(unwrappedProcessingEnv);
        this.trees = Trees.instance(processingEnv);
        this.objectType = getType(Object.class);
        this.protoObjectType = getType(ProtobufObject.class);
        this.protoMessageType = getType(ProtobufMessage.class);
        this.protoEnumType = getType(ProtobufEnum.class);
        this.collectionType = getCollectionType();
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

    private TypeMirror getCollectionType() {
        var element = processingEnv.getElementUtils().getTypeElement(Collection.class.getName());
        return erase(element.asType());
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
    }

    private void checkConverter(ExecutableElement entry) {
        var isStatic = entry.getModifiers().contains(Modifier.STATIC);
        if(isStatic && entry.getParameters().size() != 1) {
            printError("Illegal method: a static method annotated with @ProtobufConverter should have a single parameter", entry);
        }else if(!isStatic && !entry.getParameters().isEmpty()) {
            printError("Illegal method: a method annotated with @ProtobufConverter should have no parameters", entry);
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
        try {
            var objects = getProtobufObjects(roundEnv);
            for(var object : objects) {
                var result = processElement(object);
                if (result.isEmpty()) {
                    continue;
                }

                var packageName = processingEnv.getElementUtils().getPackageOf(result.get().element());
                var simpleGeneratedClassName = result.get().generatedClassName();
                var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;
                var sourceFile = processingEnv.getFiler().createSourceFile(qualifiedGeneratedClassName);
                try (var writer = new PrintWriter(sourceFile.openWriter())) {
                    if(packageName != null) {
                        writer.println("package %s;\n".formatted(packageName.getQualifiedName()));
                    }

                    var imports = getImports(result.get());
                    imports.forEach(entry -> writer.println("import %s;".formatted(entry)));
                    if(!imports.isEmpty()){
                        writer.println();
                    }

                    writer.println("public class %s {".formatted(result.get().generatedClassName()));
                    var serializationVisitor = new ProtobufSerializationVisitor(result.get(), writer);
                    serializationVisitor.instrument();
                    var deserializationVisitor = new ProtobufDeserializationVisitor(result.get(), writer);
                    deserializationVisitor.instrument();
                    writer.println("}");
                }
            }
        }catch (IOException exception) {
            throw new UncheckedIOException("Cannot open writer", exception);
        }
    }

    protected List<String> getImports(ProtobufMessageElement message) {
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

    private Optional<ProtobufMessageElement> processElement(TypeElement object) {
        return switch (object.getKind()) {
            case ENUM -> processEnum(object);
            case RECORD, CLASS -> processMessage(object);
            default -> processUnknown(object);
        };
    }

    private Optional<ProtobufMessageElement> processUnknown(TypeElement object) {
        printError("Only classes, records and enums can implement ProtobufObject", object);
        return Optional.empty();
    }

    private Optional<ProtobufMessageElement> processMessage(TypeElement message) {
        var messageElement = new ProtobufMessageElement(message, null);
        var types = processProperties(messageElement);
        if(types.isEmpty()) {
            printWarning("No properties found", message);
            return Optional.of(messageElement);
        }

        if (!hasPropertiesConstructor(message, types)) {
            printError("Missing protobuf constructor: a protobuf message must provide a constructor that takes only its properties, following their declaration order, as parameters", message);
            return Optional.empty();
        }

        return Optional.of(messageElement);
    }

    private boolean hasPropertiesConstructor(TypeElement message, List<TypeMirror> expectedParameterTypes) {
        return message.getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof ExecutableElement)
                .map(entry -> (ExecutableElement) entry)
                .filter(entry -> entry.getKind() == ElementKind.CONSTRUCTOR)
                .anyMatch(entry -> isApplicableConstructor(expectedParameterTypes, entry));
    }

    // No var args support as it's not needed
    private boolean isApplicableConstructor(List<TypeMirror> expectedParameterTypes, ExecutableElement constructor) {
        var expectedSize = expectedParameterTypes.size();
        var constructorParameters = constructor.getParameters();
        return expectedSize == constructorParameters.size()
                && IntStream.range(0, expectedSize).allMatch(index -> isSubType(constructorParameters.get(index).asType(), expectedParameterTypes.get(index)));
    }

    private List<TypeMirror> processProperties(ProtobufMessageElement messageElement) {
        return messageElement.element()
                .getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof VariableElement)
                .map(entry -> (VariableElement) entry)
                .filter(variableElement -> processProperty(messageElement, variableElement))
                .map(VariableElement::asType)
                .toList();
    }

    private boolean processProperty(ProtobufMessageElement messageElement, VariableElement variableElement) {
        var propertyAnnotation = variableElement.getAnnotation(ProtobufProperty.class);
        if(propertyAnnotation == null) {
            return false;
        }

        if(propertyAnnotation.required() && !isValidRequiredProperty(variableElement)) {
            return true;
        }

        if(propertyAnnotation.packed() && !isValidPackedProperty(variableElement, propertyAnnotation)) {
           return true;
        }

        if(propertyAnnotation.repeated() && !checkRepeatedField(variableElement)) {
            return true;
        }

        var type = getImplementationType(variableElement, propertyAnnotation);
        if(type.isEmpty()) {
            return true;
        }

        var error = messageElement.addProperty(variableElement, type.get(), propertyAnnotation);
        if(error.isEmpty()) {
            return true;
        }

        printError("Duplicated message property: %s and %s with index %s".formatted(variableElement.getSimpleName(), error.get().name(), propertyAnnotation.index()), variableElement);
        return true;
    }

    private Optional<ProtobufPropertyType> getImplementationType(VariableElement fieldElement, ProtobufProperty property) {
        var fieldType = fieldElement.asType();
        var implementationAstType = getExplicitImplementationType(fieldElement, property);
        if (!property.repeated()) {
            var rawFieldType = erase(fieldType);
            var result = new ProtobufPropertyType(
                    rawFieldType,
                    implementationAstType.orElse(rawFieldType),
                    null,
                    getNullableConverter(property, implementationAstType.orElse(null), fieldType),
                    isEnum(fieldType)
            );
            return Optional.of(result);
        }

        var collectionTypeParameter = (DeclaredType) getCollectionType(fieldType).orElse(null);
        if(collectionTypeParameter == null) {
            printError("Type inference error: cannot determine collection's type. Specify the implementation explicitly in @ProtobufProperty", fieldElement);
            return Optional.empty();
        }

        var actualCollectionTypeParameter = implementationAstType.orElse(collectionTypeParameter);
        var result = new ProtobufPropertyType(
                collectionTypeParameter,
                actualCollectionTypeParameter,
                fieldType,
                getNullableConverter(property, implementationAstType.orElse(null), actualCollectionTypeParameter),
                isEnum(actualCollectionTypeParameter)
        );
        return Optional.of(result);
    }

    private ProtobufPropertyConverter getNullableConverter(ProtobufProperty property, TypeMirror implementation, TypeMirror fieldType) {
        if (implementation == null) {
            var wrapperType = getType(property.type().wrappedType());
            if (isSameType(fieldType, getType(property.type().primitiveType())) || isSubType(fieldType, wrapperType)) {
                return null;
            }

            return getConverter(fieldType, wrapperType)
                    .orElse(null);
        }

        if(isSubType(fieldType, implementation)) {
            return null;
        }

        return getConverter(implementation, fieldType)
                .orElse(null);
    }

    private Optional<ProtobufPropertyConverter> getConverter(TypeMirror to, TypeMirror from) {
        if (!(to instanceof DeclaredType declaredType)) {
            return Optional.empty();
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

        if(serializer == null) {
            printError("Missing converter: cannot find a serializer for %s".formatted(from), declaredType.asElement());
            return Optional.empty();
        }

        if(deserializer == null) {
            printError("Missing converter: cannot find a deserializer for %s".formatted(from), declaredType.asElement());
            return Optional.empty();
        }

        var result = new ProtobufPropertyConverter(serializer, deserializer);
        return Optional.of(result);
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
            printError("Illegal implementation type: concrete class or record type required", element);
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

    private Optional<TypeMirror> getCollectionType(TypeMirror mirror) {
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

        return getCollectionType(implemented)
                .flatMap(result -> getConcreteCollectionArgumentType(result, declaredType));
    }

    private Optional<TypeMirror> getCollectionTypeBySuperClass(DeclaredType declaredType, TypeElement typeElement) {
        if (!(typeElement.getSuperclass() instanceof DeclaredType superDeclaredType)) {
            return Optional.empty();
        }

        return getCollectionType(superDeclaredType)
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
        return processingEnv.getTypeUtils().isSubtype(child, parent);
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
                .filter(index -> hasProtobufIndexAnnotation(constructor, index))
                .mapToObj(index -> getEnumMetadata(constructor, constructor.getParameters().get(index), index, constructorTree, fields))
                .reduce((first, second) -> {
                    printError("Duplicated protobuf enum index: an enum's constructor should provide only one parameter annotated with @ProtobufEnumIndex", second.parameter());
                    return first;
                });
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
