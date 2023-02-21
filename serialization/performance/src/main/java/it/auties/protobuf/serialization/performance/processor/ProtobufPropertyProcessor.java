package it.auties.protobuf.serialization.performance.processor;

import it.auties.protobuf.base.ProtobufConverter;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.serialization.performance.model.ProtobufWritable;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes(ProtobufProperty.ANNOTATION_NAME)
public class ProtobufPropertyProcessor extends AbstractProcessor {
    private static final Map<String, String> PRIMITIVES_MAP = Map.of(
            "byte", "Byte",
            "char", "Character",
            "short", "Short",
            "int", "Integer",
            "long", "Long",
            "float", "Float",
            "double", "Double",
            "boolean", "Boolean"
    );
    private static final String PROTOBUF_NAME = ProtobufMessage.class.getName();
    private static final String HASH_MAP_BUILDER = "new java.util.HashMap<>(){{%s}}";
    private static final String BUILDER_INSTRUCTION = "%s::new";
    private static final String BUILD_INSTRUCTION = "(%s e) -> e.build()";
    private static final String SETTER_ENTRY = "put(%s, (java.util.function.BiConsumer<%s, %s>) %s::%s);";
    private static final String GETTER_ENTRY = "put(%s, (%s e) -> e.%s());";
    private static final String RECORD_ENTRY = "put(%s, new it.auties.protobuf.serialization.performance.model.ProtobufEntry(%s, it.auties.protobuf.base.ProtobufType.%s, %s, %s));";
    private static final String PROPERTIES_INSTRUCTION = "new it.auties.protobuf.serialization.performance.model.ProtobufProperties(%s, %s, %s, %s, %s, %s)";
    private static final String PROPERTIES_ENTRY = "put(%s.class, %s);";
    private static final String PROPERTIES_FIELD = "public static final java.util.Map<java.lang.Class<?>, it.auties.protobuf.serialization.performance.model.ProtobufProperties> properties = new java.util.HashMap<>(){{";
    private static final String PACKAGE_NAME = "it.auties.protobuf";
    private static final String CLASS_NAME = "ProtobufStubs";
    private static final String QUALIFIED_CLASS_NAME = PACKAGE_NAME + "." + CLASS_NAME;
    private static final String CONVERTER_EXPRESSION = "%s::%s";
    private static final String VALUE_EXPRESSION = "(%s e) -> e.%s()";

    private static final Set<String> processed = new HashSet<>();
    private static PrintWriter writer;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (writer == null) {
            try {
                var file = processingEnv.getFiler().createSourceFile(QUALIFIED_CLASS_NAME);
                writer = new PrintWriter(file.openWriter());
                writer.println("package %s;".formatted(PACKAGE_NAME));
                writer.println("class %s {".formatted(CLASS_NAME));
                writer.println(PROPERTIES_FIELD);
            }catch (IOException exception) {
                throw new UncheckedIOException("Cannot create file", exception);
            }
        }

        roundEnv.getRootElements().forEach(this::analyze);
        finishEnd(roundEnv);
        return false;
    }

    private void finishEnd(RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            return;
        }

        writer.println("}};");
        writer.println("}");
        writer.close();
    }

    private void analyze(Element element) {
        element.getEnclosedElements().forEach(this::analyze);
        if (!(element instanceof TypeElement typeElement) || typeElement.getKind() == ElementKind.ENUM) {
            return;
        }

        if (!hasProtobufMessage(typeElement) || processed.contains(typeElement.toString())) {
            return;
        }

        var classQualifiedName = typeElement.toString();
        processed.add(classQualifiedName);
        var classEntry = getClassEntry(typeElement);
        var staticConstructor = getStaticConstructor(typeElement);
        var localConverter = getConverter(typeElement);
        var fields = getProtobufFields(typeElement);
        var builderClassName = "%s.%sBuilder".formatted(classQualifiedName, classEntry.className());
        var builderExpression = staticConstructor != null ? "null" : BUILDER_INSTRUCTION.formatted(builderClassName);
        var buildExpression = staticConstructor != null ? CONVERTER_EXPRESSION.formatted(classQualifiedName, staticConstructor.getSimpleName()) : BUILD_INSTRUCTION.formatted(builderClassName);
        var setters = createSetterInstruction(builderClassName, fields);
        var getters = createGetterInstruction(classQualifiedName, fields);
        var converter = localConverter != null ? VALUE_EXPRESSION.formatted(classQualifiedName, localConverter.getSimpleName()) : "null";
        var records = createRecordInstruction(fields);
        var entry = PROPERTIES_INSTRUCTION.formatted(builderExpression, buildExpression, getters, setters, converter, records);
        writer.print(PROPERTIES_ENTRY.formatted(classQualifiedName, entry));
    }

    private Element getStaticConstructor(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
                .stream()
                .filter(entry -> isProtobufConverter(entry, true))
                .findAny()
                .orElse(null);
    }

    private Element getConverter(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
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

        return new ProtobufWritable(
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

    private String parseImplementation(Element entry, ProtobufProperty annotation, TypeMirror implementation) {
        if(annotation.repeated()){
            return getRawType(entry.asType().toString());
        }

        if (!annotation.type().isMessage()) {
            var rawType = getRawType(entry.asType().toString());
            return PRIMITIVES_MAP.getOrDefault(rawType, rawType);
        }

        return implementation.toString().equals(PROTOBUF_NAME) ? getRawType(entry.asType().toString())
                : getRawType(implementation.toString());
    }

    private String getRawType(String type) {
        var parameterStart = type.indexOf("<");
        return parameterStart != -1 ? type.substring(0, parameterStart) : type;
    }

    private String createRecordInstruction(List<ProtobufWritable> fields) {
        var setters = createRecords(fields);
        return HASH_MAP_BUILDER.formatted(setters);
    }

    private String createRecords(List<ProtobufWritable> fields) {
        return fields.stream()
                .map(this::createRecord)
                .collect(Collectors.joining(""));
    }

    private String createRecord(ProtobufWritable entry) {
        String implementationType = getRecordImplementation(entry);
        var flags = ProtobufAnnotation.getFlags(entry);
        return RECORD_ENTRY.formatted(entry.index(), entry.index(), entry.type().name(), implementationType, flags);
    }

    private String getRecordImplementation(ProtobufWritable entry) {
        return entry.implementation() == null ? "null" : "%s.class".formatted(entry.implementation());
    }

    private String createSetterInstruction(String builderClassName, List<ProtobufWritable> fields) {
        var setters = createSetters(builderClassName, fields);
        return HASH_MAP_BUILDER.formatted(setters);
    }

    private String createSetters(String builderClassName, List<ProtobufWritable> fields) {
        return fields.stream()
                .map(entry -> createSetter(builderClassName, entry))
                .collect(Collectors.joining(""));
    }

    private String createSetter(String builderClassName, ProtobufWritable entry) {
        return SETTER_ENTRY.formatted(entry.index(), builderClassName, entry.implementation(), builderClassName, entry.name());
    }

    private String createGetterInstruction(String className, List<ProtobufWritable> fields) {
        var Getters = createGetters(className, fields);
        return HASH_MAP_BUILDER.formatted(Getters);
    }

    private String createGetters(String className, List<ProtobufWritable> fields) {
        return fields.stream()
                .map(entry -> createGetter(className, entry))
                .collect(Collectors.joining(""));
    }

    private String createGetter(String className, ProtobufWritable entry) {
        return GETTER_ENTRY.formatted(entry.index(), className, entry.name());
    }

    private boolean hasProtobufMessage(TypeElement typeElement) {
        return typeElement.getInterfaces()
                .stream()
                .anyMatch(entry -> entry.toString().equals(PROTOBUF_NAME));
    }
}
