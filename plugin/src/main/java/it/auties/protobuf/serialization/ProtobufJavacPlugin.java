package it.auties.protobuf.serialization;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.serialization.instrumentation.ProtobufDeserializationVisitor;
import it.auties.protobuf.serialization.instrumentation.ProtobufSerializationVisitor;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
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
        "it.auties.protobuf.annotation.ProtobufMessage",
        "it.auties.protobuf.annotation.ProtobufEnum",
        "it.auties.protobuf.annotation.ProtobufProperty",
        "it.auties.protobuf.annotation.ProtobufEnumIndex"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions("output")
public class ProtobufJavacPlugin extends AbstractProcessor implements TaskListener {
    private Map<String, ProtobufMessageElement> results;
    private Trees trees;
    private TypeMirror objectType;
    private TypeMirror collectionType;
    private Path outputDirectory;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.results = new HashMap<>();
        this.trees = Trees.instance(processingEnv);
        this.objectType = getObjectClass();
        this.collectionType = getCollectionType();
        this.outputDirectory = getOutputDirectory();
        var task = JavacTask.instance(processingEnv);
        task.addTaskListener(this);
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

    private TypeMirror getObjectClass() {
        var result = processingEnv.getElementUtils().getTypeElement(Object.class.getName());
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
        var classWriter = new ClassWriter(element.classReader(), ClassWriter.COMPUTE_MAXS);
        element.classReader().accept(classWriter, 0);
        if(!element.isEnum()){
            createSerializer(classWriter, element);
        }

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
        processMessages(roundEnv);
        processEnums(roundEnv);
        return true;
    }

    private void checkAnnotations(RoundEnvironment roundEnv) {
        checkAnnotations(
                roundEnv,
                ProtobufMessage.class,
                ProtobufProperty.class,
                "All fields annotated with @ProtobufProperty should be enclosed by a class or record annotated with @ProtobufMessage"
        );
        checkAnnotations(
                roundEnv,
                ProtobufEnum.class,
                ProtobufEnumIndex.class,
                "All fields annotated with @ProtobufEnumIndex should be enclosed by an enum annotated with @ProtobufEnum"
        );
    }

    private void checkAnnotations(RoundEnvironment roundEnv, Class<? extends Annotation> parentAnnotation, Class<? extends Annotation> annotation, String error) {
        roundEnv.getElementsAnnotatedWith(annotation)
                .stream()
                .filter(property -> getEnclosingTypeElement(property).getAnnotation(parentAnnotation) == null)
                .forEach(property -> printError(error, property));
    }

    private TypeElement getEnclosingTypeElement(Element element) {
        Objects.requireNonNull(element);
        if(element instanceof TypeElement typeElement) {
            return typeElement;
        }

        return getEnclosingTypeElement(element.getEnclosingElement());
    }

    private void processMessages(RoundEnvironment roundEnv) {
        var messages = roundEnv.getElementsAnnotatedWith(ProtobufMessage.class);
        for(var message : messages) {
            if(message.getKind() != ElementKind.CLASS && message.getKind() != ElementKind.RECORD) {
                printError("@ProtobufMessage can only be used on classes and records", message);
                continue;
            }

            var messageElement = createMessageElement(message);
            var propertiesCount = processProperties(messageElement);
            if(propertiesCount != 0) {
                continue;
            }

            printWarning("No properties found", message);
        }
    }

    private ProtobufMessageElement createMessageElement(Element element) {
        var typeElement = (TypeElement) element;
        var binaryName = getBinaryName(typeElement);
        var targetFile = outputDirectory.resolve(binaryName + ".class");
        var result = new ProtobufMessageElement(binaryName, typeElement, targetFile);
        results.put(binaryName, result);
        return result;
    }

    private String getBinaryName(TypeElement element) {
        return processingEnv.getElementUtils()
                .getBinaryName(element)
                .toString()
                .replaceAll("\\.", "/");
    }

    private long processProperties(ProtobufMessageElement messageElement) {
        return messageElement.element()
                .getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof VariableElement)
                .map(entry -> (VariableElement) entry)
                .filter(variableElement -> processProperty(messageElement, variableElement))
                .count();
    }

    private boolean processProperty(ProtobufMessageElement messageElement, VariableElement variableElement) {
        var propertyAnnotation = variableElement.getAnnotation(ProtobufProperty.class);
        if(propertyAnnotation == null) {
            return false;
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

    private Optional<ProtobufPropertyType> getImplementationType(VariableElement element, ProtobufProperty property) {
        var type = element.asType();
        var rawImplementation = getMirroredImplementation(property);
        if (!processingEnv.getTypeUtils().isSameType(rawImplementation, objectType)) {
            var result = new ProtobufPropertyType(
                    toAsmType(rawImplementation),
                    property.repeated() ? toAsmType(erase(type)) : null
            );
            return Optional.of(result);
        }

        if (!property.repeated()) {
            var result = new ProtobufPropertyType(toAsmType(type), null);
            return Optional.of(result);
        }

        var declaredType = (DeclaredType) type;
        var typeArguments = declaredType.getTypeArguments();
        if(typeArguments.isEmpty()) {
            printError("Repeated fields cannot be represented by a raw type: specify a type parameter(List<Something>) or an implementation(@ProtobufProperty(implementation = Something.class))", element);
            return Optional.empty();
        }

        // TODO: Infer the right type argument from the context
        if(typeArguments.size() > 1) {
            printError("Repeated fields cannot be represented by a Collection type with more than one type argument", element);
            return Optional.empty();
        }

        var argumentType = toAsmType(typeArguments.get(0));
        var result = new ProtobufPropertyType(argumentType, toAsmType(type));
        return Optional.of(result);
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

    private DeclaredType getMirroredImplementation(ProtobufProperty property) {
        try {
            var implementation = property.implementation();
            throw new IllegalArgumentException(implementation.toString());
        }catch (MirroredTypeException exception) {
            return (DeclaredType) exception.getTypeMirror();
        }
    }

    private boolean checkRepeatedField(VariableElement variableElement) {
        if (isCollection(variableElement.asType())) {
            return true;
        }

        printError("Type mismatch: the type of a repeated field must extend java.lang.Collection", variableElement);
        return false;
    }

    private boolean isCollection(TypeMirror type) {
        return processingEnv.getTypeUtils().isSubtype(type, collectionType);
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

    private void processEnums(RoundEnvironment roundEnv) {
        var enums = roundEnv.getElementsAnnotatedWith(ProtobufEnum.class);
        for(var entry : enums) {
            if(entry.getKind() != ElementKind.ENUM) {
                printError("@ProtobufEnum can only be used on enums", entry);
                continue;
            }

            var enumElement = (TypeElement) entry;
            var index = getEnumIndex(enumElement);
            if(index.isEmpty()) {
                continue;
            }

            var messageElement = createMessageElement(enumElement);
            var constantsCount = processEnumConstants(messageElement, index.getAsInt());
            if(constantsCount != 0) {
                continue;
            }

            printWarning("No constants found", enumElement);
        }
    }

    private long processEnumConstants(ProtobufMessageElement messageElement, int index) {
        var enumTree = (ClassTree) trees.getTree(messageElement.element());
        return enumTree.getMembers()
                .stream()
                .filter(member -> member instanceof VariableTree)
                .map(member -> (VariableTree) member)
                .peek(variableTree -> processEnumConstant(messageElement, messageElement.element(), variableTree, index))
                .count();
    }

    private OptionalInt getEnumIndex(TypeElement enumElement) {
        var constructors = getConstructors(enumElement);
        var index = -1;
        for (var constructor : constructors) {
            var match = getProtobufEnumIndexParameters(constructor);
            switch (match.length) {
                case 0 -> {}
                case 1 -> {
                    if (index != -1) {
                        printError("Duplicated protobuf constructor: an enum should provide only one constructor with a scalar parameter annotated with @ProtobufEnumIndex", constructor);
                    }

                    index = match[0];
                }
                default -> printError("Duplicated protobuf enum index: an enum's constructor should provide only one parameter annotated with @ProtobufEnumIndex", constructor);
            }
        }

        if (index == -1) {
            printError("Missing protobuf enum constructor: an enum should provide a constructor with a scalar parameter annotated with @ProtobufEnumIndex", enumElement);
            return OptionalInt.empty();
        }

        return OptionalInt.of(index);
    }

    private int[] getProtobufEnumIndexParameters(ExecutableElement constructor) {
        return IntStream.range(0, constructor.getParameters().size())
                .filter(i -> hasEnumIndex(constructor, i))
                .toArray();
    }

    private boolean hasEnumIndex(ExecutableElement constructor, int i) {
        var annotation = constructor.getParameters()
                .get(i)
                .getAnnotation(ProtobufEnumIndex.class);
        return annotation != null;
    }

    private List<ExecutableElement> getConstructors(TypeElement enumElement) {
        return enumElement.getEnclosedElements()
                .stream()
                .filter(entry -> entry instanceof ExecutableElement)
                .map(entry -> (ExecutableElement) entry)
                .filter(entry -> entry.getKind() == ElementKind.CONSTRUCTOR)
                .toList();
    }

    private void processEnumConstant(ProtobufMessageElement messageElement, TypeElement enumElement, VariableTree enumConstantTree, int index) {
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

        var indexArgument = newClassTree.getArguments().get(index);
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

    private void printWarning(String msg, Element constructor) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, constructor);
    }

    private void printError(String msg, Element constructor) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, constructor);
    }
}
