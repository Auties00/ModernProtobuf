package it.auties.protobuf.serialization.performance.processor;

import it.auties.protobuf.base.ProtobufConverter;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.serialization.performance.model.ProtobufTypeImplementation;
import it.auties.protobuf.serialization.performance.model.ProtobufWritable;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes(ProtobufProperty.ANNOTATION_NAME)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ProtobufPropertyProcessor extends AbstractProcessor {
    private static final Map<String, String> PRIMITIVES_MAP = Map.of("byte", "Byte", "char", "Character", "short", "Short", "int", "Integer", "long", "Long", "float", "Float", "double", "Double", "boolean", "Boolean");
    private static final String PROTOBUF_NAME = ProtobufMessage.class.getName();
    private static final String BUILDER_INSTRUCTION = "%s::builder";
    private static final String BUILD_INSTRUCTION = "(%s e) -> e.build()";
    private static final String SETTER_ENTRY = "(java.util.function.BiConsumer<%s, %s>) %s::%s";
    private static final String SETTER_CONVERTER_ENTRY = "(java.util.function.BiConsumer<%s, Object>) (k, v) -> k.%s(%s.%s(v))";
    private static final String SETTER_ENUM_ENTRY = "(java.util.function.BiConsumer<%s, Integer>) (k, v) -> k.%s(%s.%s(v))";
    private static final String SETTER_ENUM_ENTRY_FALLBACK = "(java.util.function.BiConsumer<%s, Integer>) (k, v) -> { if(v < %s.values().length) k.%s(%s.values()[v]); }";
    private static final String GETTER_ENTRY = "(%s e) -> e.%s()";
    private static final String GETTER_ENTRY_OPTIONAL = "(%s e) -> e.%s().orElse(null)";
    private static final String GETTER_INDEX_ENTRY = "(%s e) -> { var k = e.%s(); return k != null ? k.index() : null; }";
    private static final String GETTER_INDEX_ENTRY_OPTIONAL = "(%s e) -> {var k = e.%s(); return k.isPresent() ? k.get().index() : null; }";
    private static final String GETTER_ORDINAL_ENTRY = "(%s e) -> { var v = e.%s(); return v != null ? v.ordinal() : null; }";
    private static final String GETTER_ORDINAL_ENTRY_OPTIONAL = "(%s e) -> { var v = e.%s().orElse(null); return v != null ? v.ordinal() : null; }";
    private static final String GETTER_CONVERTED_ENTRY = "(%s e) -> { var v =  e.%s(); return v != null ? v.%s() : null; }";
    private static final String GETTER_CONVERTED_ENTRY_OPTIONAL = "(%s e) -> { var v =  e.%s(); return v.isPresent() ? v.get().%s() : null; }";
    private static final String RECORD_ENTRY = "new it.auties.protobuf.serialization.performance.model.ProtobufField(%s, it.auties.protobuf.base.ProtobufType.%s, %s, %s)";
    private static final String MODEL_INSTRUCTION = "new it.auties.protobuf.serialization.performance.model.ProtobufModel(%s, %s, new java.util.HashMap<>(){{%s}})";
    private static final String ACCESSORS_INSTRUCTION = "put(%s, new it.auties.protobuf.serialization.performance.model.ProtobufAccessors(%s, %s, %s));";
    private static final String ACCESSORS_INSTRUCTION_REPEATED = "put(%s, new it.auties.protobuf.serialization.performance.model.ProtobufAccessors(%s, %s, %s, %s));";
    private static final String PROPERTIES_ENTRY = "put(%s.class, %s);";
    private static final String PROPERTIES_FIELD = "public static final java.util.Map<java.lang.Class<?>, it.auties.protobuf.serialization.performance.model.ProtobufModel> properties = new java.util.HashMap<>(){{";
    private static final String PACKAGE_NAME = "it.auties.protobuf";
    private static final String CLASS_NAME = "ProtobufStubs";
    private static final String QUALIFIED_CLASS_NAME = PACKAGE_NAME + "." + CLASS_NAME;
    private static final String CONVERTER_EXPRESSION = "%s::%s";
    private static final String REPEATED_BUILDER = "() -> new %s<%s>()";

    private static PrintWriter writer;
    private static final Set<String> processed = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        createFile();
        roundEnv.getRootElements().forEach(this::analyze);
        finishEnd(roundEnv);
        return false;
    }

    private void createFile() {
        if (writer != null) {
            return;
        }

        try {
            var file = processingEnv.getFiler().createSourceFile(QUALIFIED_CLASS_NAME);
            writer = new PrintWriter(file.openWriter());
            writer.println("package %s;".formatted(PACKAGE_NAME));
            writer.println("class %s {".formatted(CLASS_NAME));
            writer.print(PROPERTIES_FIELD);
        }catch (IOException exception) {
            throw new UncheckedIOException("Cannot create file", exception);
        }
    }

    private void finishEnd(RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            return;
        }

        writer.print("}};");
        writer.print("}");
        writer.close();
    }

    private void analyze(Element element) {
        element.getEnclosedElements().forEach(this::analyze);
        if (!(element instanceof TypeElement typeElement) || isEnum(typeElement) || isAbstract(typeElement)) {
            return;
        }

        if (!hasProtobufMessage(typeElement) || processed.contains(typeElement.toString())) {
            return;
        }

        var classQualifiedName = typeElement.toString();
        processed.add(classQualifiedName);
        var classEntry = getClassEntry(typeElement);
        var staticConstructor = getStaticConstructor(typeElement);
        var builderClassName = "%s.%sBuilder".formatted(classQualifiedName, classEntry.className());
        var builderExpression = staticConstructor != null ? "null" : BUILDER_INSTRUCTION.formatted(classQualifiedName);
        var buildExpression = staticConstructor != null ? CONVERTER_EXPRESSION.formatted(classQualifiedName, staticConstructor.getSimpleName()) : BUILD_INSTRUCTION.formatted(builderClassName);
        var fields = createFields(typeElement, classQualifiedName, builderClassName);
        var entry = MODEL_INSTRUCTION.formatted(builderExpression, buildExpression, fields);
        writer.print(PROPERTIES_ENTRY.formatted(classQualifiedName, entry));
    }

    private boolean isEnum(TypeElement typeElement) {
        return typeElement.getKind() == ElementKind.ENUM;
    }

    private boolean isAbstract(TypeElement typeElement){
        return typeElement.getKind() == ElementKind.INTERFACE
                || (typeElement.getKind() == ElementKind.CLASS && typeElement.getModifiers().contains(Modifier.ABSTRACT));
    }

    private String createFields(TypeElement typeElement, String classQualifiedName, String builderClassName) {
        return getProtobufFields(typeElement).stream()
                .map(field -> createField(classQualifiedName, builderClassName, field))
                .collect(Collectors.joining(""));
    }

    private String createField(String classQualifiedName, String builderClassName, ProtobufWritable field) {
        var getter = createGetter(classQualifiedName, field);
        var setter = createSetter(builderClassName, field);
        var record = createRecord(field);
        if(field.repeated()){
            var rawCollectionType = field.implementation().rawType();
            var collectionType = processingEnv.getElementUtils().getTypeElement(rawCollectionType);
            var concreteCollectionType = isAbstract(collectionType) ? "java.util.ArrayList" : rawCollectionType;
            var repeatedBuilder = REPEATED_BUILDER.formatted(concreteCollectionType, field.implementation().parameterType());
            return ACCESSORS_INSTRUCTION_REPEATED.formatted(field.index(), getter, setter, repeatedBuilder, record);
        }

        return ACCESSORS_INSTRUCTION.formatted(field.index(), getter, setter, record);
    }

    private Element getStaticConstructor(TypeElement typeElement) {
        return typeElement == null ? null : typeElement.getEnclosedElements()
                .stream()
                .filter(entry -> isProtobufConverter(entry, true))
                .findAny()
                .orElse(null);
    }

    private ExecutableElement getConverter(TypeElement typeElement) {
        return typeElement == null ? null : (ExecutableElement) typeElement.getEnclosedElements()
                .stream()
                .filter(entry -> isProtobufConverter(entry, false))
                .findAny()
                .orElse(null);
    }

    private boolean isProtobufConverter(Element entry, boolean read) {
        return entry.getAnnotation(ProtobufConverter.class) != null
                && read == entry.getModifiers().contains(Modifier.STATIC);
    }

    private ClassEntry getClassEntry(TypeElement element) {
        var packageElement = processingEnv.getElementUtils().getPackageOf(element);
        if (packageElement == null) {
            return new ClassEntry(null, element.toString());
        }

        var packageName = packageElement.getQualifiedName().toString();
        var index = element.toString().lastIndexOf(".") + 1;
        var className = element.toString().substring(index);
        return new ClassEntry(packageName, className);
    }

    private record ClassEntry(String packageName, String className) { }

    private List<ProtobufWritable> getProtobufFields(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
                .stream()
                .map(this::getProtobufField)
                .filter(Objects::nonNull)
                .toList();
    }

    private ProtobufWritable getProtobufField(Element entry) {
        var annotation = entry.getAnnotation(ProtobufProperty.class);
        if (annotation == null) {
            return null;
        }

        TypeMirror implementation = null;
        try {
            var ignored = annotation.implementation();
        }catch (MirroredTypeException exception){
            implementation = exception.getTypeMirror();
        }
        Objects.requireNonNull(implementation);
        var type = processingEnv.getTypeUtils().asElement(entry.asType());
        return new ProtobufWritable(
                (TypeElement) entry.getEnclosingElement(),
                (TypeElement) type,
                entry.getSimpleName().toString(),
                annotation.index(),
                annotation.type(),
                parseImplementation(entry, annotation, implementation),
                annotation.required(),
                annotation.ignore(),
                annotation.packed(),
                annotation.repeated()
        );
    }

    private ProtobufTypeImplementation parseImplementation(Element entry, ProtobufProperty annotation, TypeMirror implementation) {
        if (!annotation.type().isMessage()) {
            return new ProtobufTypeImplementation(getRawType(entry.asType().toString()), null);
        }

        if (!implementation.toString().equals(PROTOBUF_NAME)) {
            var implementationType = getRawType(implementation.toString());
            if(annotation.repeated()){
                return new ProtobufTypeImplementation(getRawType(entry.asType().toString()), implementationType);
            }

            return new ProtobufTypeImplementation(getRawType(implementation.toString()), null);
        }

        if (!annotation.repeated()) {
            return new ProtobufTypeImplementation(getRawType(entry.asType().toString()), null);
        }

        var rawType = getRawType(entry.asType().toString());
        var type = (DeclaredType) entry.asType();
        var size = type.getTypeArguments().size();
        if (size == 0) {
            return new ProtobufTypeImplementation(rawType, null);
        }

        var parameterType = type.getTypeArguments().get(size - 1).toString();
        return new ProtobufTypeImplementation(rawType, parameterType);
    }

    private String getRawType(String type) {
        var actualType = type.replaceAll("@.+ ", "");
        var parameterStart = actualType.indexOf("<");
        return parameterStart != -1 ? actualType.substring(0, parameterStart) : actualType;
    }

    private String createRecord(ProtobufWritable entry) {
        var implementationType = getRecordImplementation(entry);
        var flags = ProtobufAnnotation.getFlags(entry);
        return RECORD_ENTRY.formatted(entry.index(), entry.type().name(), implementationType, flags);
    }

    private String getRecordImplementation(ProtobufWritable entry) {
        return entry.implementation() == null ? "null" : "%s.class".formatted(entry.implementation().parameterType());
    }

    private String createSetter(String builderClassName, ProtobufWritable entry) {
        var rawType = entry.implementation().rawType();
        var type = PRIMITIVES_MAP.getOrDefault(rawType, rawType);
        var converter = getStaticConstructor(entry.element());
        if(converter != null){
            return SETTER_CONVERTER_ENTRY.formatted(builderClassName, entry.name(), type, converter.getSimpleName().toString());
        }

        if (entry.element() == null || entry.element().getKind() != ElementKind.ENUM) {
            return SETTER_ENTRY.formatted(builderClassName, type, builderClassName, entry.name());
        }

        var enumStaticConstructor = getStaticConstructor(entry.element());
        if(enumStaticConstructor != null) {
            return SETTER_ENUM_ENTRY.formatted(builderClassName, entry.name(), type, enumStaticConstructor.getSimpleName().toString());
        }

        var enumImplicitStaticConstructor = getImplicitStaticConstructor(entry);
        if(enumImplicitStaticConstructor != null) {
            return SETTER_ENUM_ENTRY.formatted(builderClassName, entry.name(), type, enumImplicitStaticConstructor.getSimpleName().toString());
        }

        return SETTER_ENUM_ENTRY_FALLBACK.formatted(builderClassName, type, entry.name(), type);
    }

    private Element getImplicitStaticConstructor(ProtobufWritable entry) {
        return entry.element() == null ? null : entry.element()
                .getEnclosedElements()
                .stream()
                .filter(candidate -> candidate.getKind() == ElementKind.METHOD
                        && candidate.getModifiers().contains(Modifier.STATIC)
                        && candidate.getSimpleName().toString().equals("of"))
                .findFirst()
                .orElse(null);
    }

    private String createGetter(String className, ProtobufWritable entry) {
        var thisConverter = getConverter(entry.enclosing());
        var thisConverterElement = thisConverter != null ? (TypeElement) processingEnv.getTypeUtils().asElement(thisConverter.getReturnType()) : entry.enclosing();
        var methodName = thisConverter != null ? "%s().%s".formatted(thisConverter.getSimpleName(), entry.name()) : entry.name();
        var converter = getConverter(entry.element());
        if (converter != null) {
            return isOptionalGetter(thisConverterElement, entry) ? GETTER_CONVERTED_ENTRY_OPTIONAL.formatted(className, methodName, converter.getSimpleName().toString())
                    : GETTER_CONVERTED_ENTRY.formatted(className, methodName, converter.getSimpleName().toString());
        }

        if (entry.element() == null || entry.element().getKind() != ElementKind.ENUM) {
            return isOptionalGetter(thisConverterElement, entry) ? GETTER_ENTRY_OPTIONAL.formatted(className, methodName)
                    : GETTER_ENTRY.formatted(className, methodName);
        }

        var indexField = getEnumIndexField(entry);
        if (indexField != null) {
            return isOptionalGetter(thisConverterElement, entry) ? GETTER_INDEX_ENTRY_OPTIONAL.formatted(className, entry.name())
                    : GETTER_INDEX_ENTRY.formatted(className, entry.name());
        }

        return isOptionalGetter(thisConverterElement, entry) ? GETTER_ORDINAL_ENTRY_OPTIONAL.formatted(className, entry.name())
                : GETTER_ORDINAL_ENTRY.formatted(className, entry.name());
    }

    private boolean isOptionalGetter(TypeElement enclosing, ProtobufWritable entry) {
        return enclosing != null && enclosing.getEnclosedElements()
                .stream()
                .filter(method -> isOptionalGetterMethod(entry, method))
                .findFirst()
                .map(method -> ((ExecutableElement) method).getReturnType())
                .map(method -> getRawType(method.toString()))
                .filter(type -> type.equals(Optional.class.getName()))
                .isPresent();
    }

    private boolean isOptionalGetterMethod(ProtobufWritable entry, Element method) {
        return method.getKind() == ElementKind.METHOD
                && method.getSimpleName().toString().equals(entry.name());
    }

    private Element getEnumIndexField(ProtobufWritable entry) {
        return entry.element()
                .getEnclosedElements()
                .stream()
                .filter(field -> field.getKind() == ElementKind.METHOD && field.getSimpleName().toString().equals("index"))
                .findFirst()
                .orElse(null);
    }

    private boolean hasProtobufMessage(TypeElement typeElement) {
        return typeElement != null && (hasProtobufMessageInterface(typeElement) || hasProtobufMessageInterface(typeElement.getSuperclass()));
    }

    private boolean hasProtobufMessageInterface(TypeElement typeElement) {
        return typeElement.getInterfaces()
                .stream()
                .anyMatch(entry -> entry.toString().equals(PROTOBUF_NAME) || hasProtobufMessageInterface(entry));
    }

    private boolean hasProtobufMessageInterface(TypeMirror mirror) {
        return hasProtobufMessage((TypeElement) processingEnv.getTypeUtils().asElement(mirror));
    }
}
