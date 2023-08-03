package it.auties.protobuf.serialization;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.ProtobufConverter;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufObject;
import it.auties.protobuf.serialization.instrumentation.ProtobufDeserializationVisitor;
import it.auties.protobuf.serialization.instrumentation.ProtobufSerializationVisitor;
import it.auties.protobuf.serialization.model.ProtobufEnumMetadata;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyConverter;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

@SupportedAnnotationTypes({
        "it.auties.protobuf.annotation.ProtobufProperty",
        "it.auties.protobuf.annotation.ProtobufEnumIndex"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ProtobufJavacPlugin extends AbstractProcessor implements TaskListener {
    private Map<String, ProtobufMessageElement> results;
    private Trees trees;
    private TypeMirror objectType;
    private TypeMirror protoObjectType;
    private TypeMirror protoMessageType;
    private TypeMirror protoEnumType;
    private TypeMirror collectionType;
    private Path outputDirectory;

    @Override
    public synchronized void init(ProcessingEnvironment wrapperProcessingEnv) {
        var unwrappedProcessingEnv = unwrapProcessingEnv(wrapperProcessingEnv);
        super.init(unwrappedProcessingEnv);
        this.results = new HashMap<>();
        this.trees = Trees.instance(processingEnv);
        this.objectType = getType(Object.class);
        this.protoObjectType = getType(ProtobufObject.class);
        this.protoMessageType = getType(ProtobufMessage.class);
        this.protoEnumType = getType(ProtobufEnum.class);
        this.collectionType = getCollectionType();
        this.outputDirectory = getOutputDirectory();
        var task = JavacTask.instance(processingEnv);
        task.addTaskListener(this);
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

    private Path getOutputDirectory() {
        try {
            var result = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "dummy");
            var path = Path.of(result.toUri());
            Files.deleteIfExists(path);
            return path.getParent();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot create resource to determine class output path", exception);
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
    public void finished(TaskEvent event) {
        if(event.getKind() != TaskEvent.Kind.GENERATE) {
            return;
        }

        var binaryName = getBinaryName(event.getTypeElement());
        var result = results.get(binaryName);
        if(result == null) {
            return;
        }

        processElement(result);
    }

    private void processElement(ProtobufMessageElement element) {
        var classWriter = new ClassWriter(element.classReader(), ClassWriter.COMPUTE_FRAMES);
        element.classReader().accept(classWriter, 0);
        createSerializer(classWriter, element);
        createDeserializer(classWriter, element);
        writeResult(classWriter, element.targetFile());
    }

    private void createSerializer(ClassWriter classWriter, ProtobufMessageElement element) {
        var serializationVisitor = new ProtobufSerializationVisitor(element, classWriter);
        serializationVisitor.instrument();
    }

    private void createDeserializer(ClassWriter classWriter, ProtobufMessageElement element) {
        var deserializationVisitor = new ProtobufDeserializationVisitor(element, classWriter);
        deserializationVisitor.instrument();
    }

    private void writeResult(ClassWriter classWriter, Path outputFile) {
        try {
            Files.write(outputFile, classWriter.toByteArray());
        }catch (IOException exception) {
            throw new UncheckedIOException("Cannot write instrumented class", exception);
        }
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
        var objects = getProtobufObjects(roundEnv);
        for(var object : objects) {
            switch (object.getKind()) {
                case ENUM -> processEnum(object);
                case RECORD, CLASS -> processMessage(object);
                default -> printError("Only classes, records and enums can implement ProtobufObject", object);
            }
        }
    }

    private void processMessage(TypeElement message) {
        var binaryName = getBinaryName(message);
        var targetFile = outputDirectory.resolve(binaryName + ".class");
        var messageElement = new ProtobufMessageElement(binaryName, message, targetFile, null);
        results.put(binaryName, messageElement);
        var types = processProperties(messageElement);
        if(types.isEmpty()) {
            printWarning("No properties found", message);
            return;
        }

        if(hasPropertiesConstructor(message, types)) {
            return;
        }

        printError("Missing protobuf constructor: a protobuf message must provide a constructor that takes only its properties, following their declaration order, as parameters", message);
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

    private String getBinaryName(TypeElement element) {
        return processingEnv.getElementUtils()
                .getBinaryName(element)
                .toString()
                .replaceAll("\\.", "/");
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
        System.out.println(type);
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
        var fieldAstType = fieldElement.asType();
        var fieldAsmType = toAsmType(erase(fieldAstType));
        var implementationAstType = getExplicitImplementationType(fieldElement, property);
        if (!property.repeated()) {
            var result = new ProtobufPropertyType(
                    fieldAsmType,
                    implementationAstType.map(this::toAsmType).orElse(fieldAsmType),
                    null,
                    getNullableConverter(property, implementationAstType.orElse(null), fieldAstType),
                    isEnum(fieldAstType)
            );
            return Optional.of(result);
        }

        var argumentAstType = (DeclaredType) getCollectionType(fieldAstType)
                .orElse(null);
        if(argumentAstType == null) {
            printError("Type inference error: cannot determine collection's type.\nSpecify the implementation explicitly in @ProtobufProperty", fieldElement);
            return Optional.empty();
        }

        var type = implementationAstType.orElse(argumentAstType);
        var result = new ProtobufPropertyType(
                toAsmType(argumentAstType),
                toAsmType(type),
                fieldAsmType,
                getNullableConverter(property, implementationAstType.orElse(null), type),
                isEnum(type)
        );
        return Optional.of(result);
    }

    private ProtobufPropertyConverter getNullableConverter(ProtobufProperty property, DeclaredType implementation, TypeMirror fieldType) {
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

        var serializerName = serializer.getSimpleName().toString();
        var serializerDescriptor = getMethodDescriptor(serializer);
        var deserializerName = deserializer.getSimpleName().toString();
        var deserializerDescriptor = getMethodDescriptor(deserializer);
        var result = new ProtobufPropertyConverter(serializerName, serializerDescriptor, deserializerName, deserializerDescriptor);
        return Optional.of(result);
    }

    private String getMethodDescriptor(ExecutableElement element) {
        var builder = new StringBuilder();
        builder.append("(");
        element.getParameters()
                .stream()
                .map(parameter -> toAsmType(parameter.asType()))
                .forEach(builder::append);
        builder.append(")");
        builder.append(toAsmType(element.getReturnType()));
        return builder.toString();
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

    private Optional<DeclaredType> getExplicitImplementationType(VariableElement element, ProtobufProperty property) {
        var rawImplementation = getMirroredImplementation(property);
        if (isSameType(rawImplementation, objectType)) {
            return Optional.empty();
        }

        if(rawImplementation.asElement().getModifiers().contains(Modifier.ABSTRACT)) {
            printError("Illegal implementation type: concrete class or record type required", element);
            return Optional.empty();
        }
        
        return Optional.of(rawImplementation);
    }

    private DeclaredType getMirroredImplementation(ProtobufProperty property) {
        try {
            var implementation = property.implementation();
            throw new IllegalArgumentException(implementation.toString());
        }catch (MirroredTypeException exception) {
            return (DeclaredType) exception.getTypeMirror();
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

    private Type toAsmType(TypeMirror mirror) {
        if(mirror.getKind() == TypeKind.ARRAY) {
            var arrayType = (ArrayType) mirror;
            var argumentType = toAsmType(arrayType.getComponentType());
            return Type.getType("[" + argumentType.getDescriptor());
        }

        if (!mirror.getKind().isPrimitive()) {
            var declaredType = (DeclaredType) mirror;
            var typeElement = (TypeElement) declaredType.asElement();
            var binaryName = getBinaryName(typeElement);
            return Type.getObjectType(binaryName);
        }

        return switch (mirror.getKind()) {
            case BOOLEAN -> Type.BOOLEAN_TYPE;
            case BYTE -> Type.BYTE_TYPE;
            case SHORT -> Type.SHORT_TYPE;
            case INT -> Type.INT_TYPE;
            case LONG -> Type.LONG_TYPE;
            case CHAR -> Type.CHAR_TYPE;
            case FLOAT -> Type.FLOAT_TYPE;
            case DOUBLE -> Type.DOUBLE_TYPE;
            default -> throw new IllegalStateException("Unexpected value: " + mirror.getKind());
        };
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

    private void processEnum(TypeElement enumElement) {
        var messageElement = createEnumElement(enumElement);
        if(messageElement.isEmpty()) {
            return;
        }

        var constantsCount = processEnumConstants(messageElement.get());
        if(constantsCount != 0) {
            return;
        }

        printWarning("No constants found", enumElement);
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
        var binaryName = getBinaryName(enumElement);
        var targetFile = outputDirectory.resolve(binaryName + ".class");
        var metadata = getEnumMetadata(enumElement);
        if (metadata.isEmpty()) {
            printError("Missing protobuf enum constructor: an enum should provide a constructor with a scalar parameter annotated with @ProtobufEnumIndex", enumElement);
            return Optional.empty();
        }

        if(metadata.get().isUnknown()) {
            return Optional.empty();
        }

        var result = new ProtobufMessageElement(binaryName, enumElement, targetFile, metadata.get());
        results.put(binaryName, result);
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
            return new ProtobufEnumMetadata(constructor, fields.enumIndexField(), toAsmType(fields.enumIndexField().asType()), parameter, index);
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
                    printError("Duplicated assignment: the parameter annotated with @ProtobufEnumIndex can be assigned to a single local field", second);
                    return first;
                })
                .map(fieldElement -> new ProtobufEnumMetadata(constructor, fieldElement, toAsmType(fieldElement.asType()), parameter, index))
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
                return new ProtobufEnumFields(variableElement, null);
            }

            fields.put(variableElement.getSimpleName(), variableElement);
        }

        return new ProtobufEnumFields(null, fields);
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
