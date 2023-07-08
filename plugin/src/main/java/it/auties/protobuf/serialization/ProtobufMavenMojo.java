package it.auties.protobuf.serialization;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import it.auties.protobuf.base.*;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static it.auties.protobuf.base.ProtobufMessage.*;
import static it.auties.protobuf.base.ProtobufWireType.*;

@Mojo(name = "protobuf", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ProtobufMavenMojo extends AbstractMojo {
    private static final String ARRAY_INPUT_STREAM = ProtobufInputStream.class.getName();
    private static final String ARRAY_OUTPUT_STREAM = ProtobufOutputStream.class.getName();
    private static final String PROTOBUF_SERIALIZATION_EXCEPTION = ProtobufSerializationException.class.getName();
    private static final String PROTOBUF_DESERIALIZATION_EXCEPTION = ProtobufDeserializationException.class.getName();
    private static final String DESERIALIZATION_ENUM_BODY = """
                public static %s %s(int index){
                        java.util.Iterator iterator = java.util.Arrays.stream(values()).iterator();
                        %s entry;
                        while(iterator.hasNext()) {
                            entry = (%s) iterator.next();
                            if(entry.index() == index) {
                                return entry;
                            }
                        }
                        return null;
                    }
            """;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    private URLClassLoader classLoader;
    
    private TypePool classPool;
    
    private final Set<String> processed = new HashSet<>();

    @Override
    public void execute() throws MojoExecutionException {
        runTransformation(false);
        runTransformation(true);
    }

    private void runTransformation(boolean test) throws MojoExecutionException {
        var time = System.currentTimeMillis();
        try {
            getLog().info("Starting to process %s".formatted(test ? "tests" : "files"));
            this.classLoader = createClassLoader(test);
            this.classPool = TypePool.Default.of(classLoader);
            runTransformation();
        } catch (Throwable exception) {
            getLog().error(exception);
            throw new MojoExecutionException("Cannot run protobuf plugin", exception);
        }finally {
            getLog().info("Processing took %sms".formatted(System.currentTimeMillis() - time));
        }
    }

    private void runTransformation() {
        getAllClasses(classLoader)
                .stream()
                .map(this::getFromClassPool)
                .flatMap(Optional::stream)
                .forEach(this::processClass);
    }

    private void processClass(TypeDescription typeDeclaration) {
        var className = typeDeclaration.getCanonicalName();
        if(processed.contains(className)){
            return;
        }

        getLog().info("Processing %s".formatted(className));
        processed.add(className);
        if(isNonConcrete(typeDeclaration)){
            getLog().info("Skipping non-concrete class");
            return;
        }

        var fields = getProtobufFields(typeDeclaration);
        createSerializationMethod(typeDeclaration, fields);
        createDeserializerMethod(typeDeclaration, fields);
    }

    private boolean isNonConcrete(TypeDescription typeDeclaration) {
        return typeDeclaration.isInterface() || typeDeclaration.isAbstract();
    }
    
    private void createSerializationMethod(TypeDescription typeDeclaration, Map<FieldDescription.InDefinedShape, ProtobufProperty> fields) {
        var bodyBuilder = new StringWriter();
        try(var body = new PrintWriter(bodyBuilder)) {
            body.println("public byte[] %s() {".formatted(SERIALIZATION_METHOD));
            body.println("%s output = new %s();".formatted(ARRAY_OUTPUT_STREAM, ARRAY_OUTPUT_STREAM));
            for (var entry : fields.entrySet()) {
                var field = entry.getKey();
                var annotation = entry.getValue();
                if (annotation.ignore()) {
                    continue;
                }

                var converter = getConverter(field, annotation, false);
                createSerializationField(typeDeclaration, body, field, annotation, converter);
            }
            body.println("return output.toByteArray();");
            body.println("}");
            addSafeMethod(typeDeclaration, SERIALIZATION_METHOD, bodyBuilder.toString());
            getLog().info("Created serialization method");
        }
    }

    private MethodDescription.InDefinedShape getConverter(FieldDescription.InDefinedShape field, ProtobufProperty annotation, boolean staticMethod) {
        var ctType = getImplementationType(field, annotation)
                .orElse(null);
        if (ctType == null || ctType.isAssignableFrom(annotation.type().primitiveType()) || ctType.isAssignableFrom(annotation.type().wrappedType())) {
            return null;
        }

        return ctType.getDeclaredMethods()
                .stream()
                .filter(entry -> getAnnotation(entry, ProtobufConverter.class) .isPresent() && staticMethod == entry.isStatic())
                .findFirst()
                .orElseThrow(() -> new ProtobufSerializationException("Missing converter in %s".formatted(ctType.getName())));
    }

    private Optional<TypeDescription> getImplementationType(FieldDescription.InDefinedShape field, ProtobufProperty annotation) {
        if (!Objects.equals(annotation.implementation().getName(), ProtobufMessage.class.getName())) {
            return getFromClassPool(annotation.implementation().getName());
        }

        if (annotation.repeated()) {
            return getFromClassPool(annotation.type().wrappedType().getName());
        }

        return getFromClassPool(field.getType().getActualName());
    }

    private void createSerializationField(TypeDescription typeDeclaration, PrintWriter body, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter) {
        var nullCheck = !field.getType().isPrimitive() && annotation.required();
        if(nullCheck) {
            body.println("if(%s == null) {".formatted(field.getName()));
            body.println("throw %s.missingMandatoryField(\"%s\");".formatted(PROTOBUF_SERIALIZATION_EXCEPTION,  field.getName()));
            body.println("}");
        }

        switch (annotation.type()){
            case MESSAGE -> createSerializationMessage(typeDeclaration, body, field, annotation, converter, nullCheck);
            case FLOAT -> createSerializationAny(typeDeclaration, body, annotation, converter, "Float", field, nullCheck);
            case DOUBLE -> createSerializationAny(typeDeclaration, body, annotation, converter, "Double", field, nullCheck);
            case BOOL -> createSerializationAny(typeDeclaration, body, annotation, converter, "Bool", field, nullCheck);
            case STRING -> createSerializationAny(typeDeclaration, body, annotation, converter, "String", field, nullCheck);
            case BYTES -> createSerializationAny(typeDeclaration, body, annotation, converter, "Bytes", field, nullCheck);
            case INT32, SINT32 -> createSerializationAny(typeDeclaration, body, annotation, converter, "Int32", field, nullCheck);
            case UINT32 -> createSerializationAny(typeDeclaration, body, annotation, converter, "UInt32", field, nullCheck);
            case FIXED32, SFIXED32 -> createSerializationAny(typeDeclaration, body, annotation, converter, "Fixed32", field, nullCheck);
            case INT64, SINT64 -> createSerializationAny(typeDeclaration, body, annotation, converter, "Int64", field, nullCheck);
            case UINT64 -> createSerializationAny(typeDeclaration, body, annotation, converter, "UInt64", field, nullCheck);
            case FIXED64, SFIXED64 -> createSerializationAny(typeDeclaration, body, annotation, converter, "Fixed64", field, nullCheck);
        }
    }

    private void createSerializationMessage(TypeDescription typeDeclaration, PrintWriter body, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter, boolean nullCheck) {
        var ctType = getImplementationType(field, annotation).orElse(null);
        if(ctType == null){
            return;
        }

        processClass(typeDeclaration);
        if(!annotation.repeated()) {
            if (nullCheck) {
                body.println("else {");
            } else {
                body.println("if(%s != null) {".formatted(field.getName()));
            }
        }

        if(ctType.isEnum()){
            if(annotation.repeated()) {
                createSerializationRepeatedFixed(typeDeclaration, field, annotation, body, nullCheck);
                body.println("output.writeUInt32(%s, entry%s.index());".formatted(annotation.index(), toMethodCall(converter)));
                body.println("}");
            }else {
                var getter = findGetter(typeDeclaration, field, annotation);
                body.println("output.writeUInt32(%s, %s%s.index());".formatted(annotation.index(), getter, toMethodCall(converter)));
            }
        }else if(annotation.repeated()){
            createSerializationRepeatedFixed(typeDeclaration, field, annotation, body, nullCheck);
            body.println("output.writeBytes(%s, entry%s.%s());".formatted(annotation.index(), toMethodCall(converter), SERIALIZATION_METHOD));
            body.println("}");
        }else {
            var getter = findGetter(typeDeclaration, field, annotation);
            body.println("output.writeBytes(%s, %s%s.%s());".formatted(annotation.index(), getter, toMethodCall(converter), SERIALIZATION_METHOD));
        }

        body.println("}");
    }

    private void createSerializationAny(TypeDescription typeDeclaration, PrintWriter body, ProtobufProperty annotation, MethodDescription.InDefinedShape converter, String writeType, FieldDescription.InDefinedShape field, boolean nullCheck) {
        if(annotation.repeated()){
            createSerializationRepeatedFixed(typeDeclaration, field, annotation, body, nullCheck);
            body.println("output.write%s(%s, entry%s);".formatted(writeType, annotation.index(), toMethodCall(converter)));
            body.println("}");
            body.println("}");
            return;
        }

        var primitive = field.getType().isPrimitive();
        if(!primitive) {
            if (nullCheck) {
                body.println("else {");
            } else {
                body.println("if(%s != null) {".formatted(field.getName()));
            }
        }

        var getter = findGetter(typeDeclaration, field, annotation);
        body.println("output.write%s(%s, %s%s);".formatted(writeType, annotation.index(), getter, toMethodCall(converter)));
        if(!primitive){
            body.println("}");
        }
    }

    private void createSerializationRepeatedFixed(TypeDescription typeDeclaration, FieldDescription.InDefinedShape field, ProtobufProperty annotation, PrintWriter body, boolean nullCheck) {
        var type = getImplementationType(field, annotation).orElse(null);
        if(type == null){
            return;
        }

        var implementationType = type.getName();
        var getter = findGetter(typeDeclaration, field, annotation);
        if(nullCheck){
            body.println("else {");
        }else {
            body.println("if(%s != null) {".formatted(field.getName()));
        }

        body.println("java.util.Iterator iterator = %s.iterator();".formatted(getter));
        body.println("%s entry;".formatted(implementationType));
        body.println("while(iterator.hasNext()) {");
        body.println("entry = (%s) iterator.next();".formatted(implementationType));
    }

    private String toMethodCall(MethodDescription.InDefinedShape converter) {
        return converter != null ? ".%s()".formatted(converter.getName()) : "";
    }

    private String findGetter(TypeDescription typeDeclaration, FieldDescription.InDefinedShape variable, ProtobufProperty annotation){
        if (!annotation.repeated() || variable.getType().asErasure().isAssignableFrom(Collection.class)) {
            return variable.getName();
        }

        var accessor = findMethod(typeDeclaration, variable.getName())
                .or(() -> findMethod(typeDeclaration, "get" + variable.getName().substring(0, 1).toUpperCase() + variable.getName().substring(1)))
                .orElseThrow(() -> new NoSuchElementException("Missing getter/accessor for repeated field %s in %s".formatted(variable.getName(), typeDeclaration.getCanonicalName())));
        if(!accessor.getReturnType().asErasure().isAssignableFrom(Collection.class)) {
            throw new IllegalStateException("Unexpected getter/accessor for repeated field %s in %s: expected return type to extend Collection".formatted(variable.getName(), typeDeclaration.getName()));
        }

        return accessor.getName() + "()";
    }

    private Optional<MethodDescription.InDefinedShape> findMethod(TypeDescription typeDeclaration, String name, Class<?>... params){
        return typeDeclaration.getDeclaredMethods()
                .asDefined()
                .stream()
                .filter(entry -> isMethodMatching(name, params, entry))
                .findFirst();
    }

    private boolean isMethodMatching(String name, Class<?>[] params, MethodDescription.InDefinedShape entry) {
        if(Objects.equals(entry.getName(), name)){
            return false;
        }

        var actualParamsIterator = Arrays.stream(params).iterator();
        var candidateParamsIterator = entry.getParameters().iterator();
        while (actualParamsIterator.hasNext()){
            var actualParameter = actualParamsIterator.next();
            if(!candidateParamsIterator.hasNext()){
                return false;
            }

            var candidateParameter = candidateParamsIterator.next();
            if(!candidateParameter.getType().asErasure().isAssignableFrom(actualParameter)){
                return false;
            }
        }

        return true;
    }

    private void createDeserializerMethod(TypeDescription typeDeclaration, Map<FieldDescription.InDefinedShape, ProtobufProperty> fields) {
        var methodName = getDeserializationMethod(typeDeclaration);
        if(typeDeclaration.isEnum()){
            var methodBody = DESERIALIZATION_ENUM_BODY.formatted(typeDeclaration.getName(), methodName, typeDeclaration.getName(), typeDeclaration.getName());
            addSafeMethod(typeDeclaration, methodName, methodBody, int.class);
            return;
        }

        var builderMethod = findMethod(typeDeclaration, "builder");
        if(builderMethod.isEmpty()){
            getLog().error("Missing builder() in %s".formatted(typeDeclaration.getName()));
            return;
        }

        var bodyBuilder = new StringWriter();
        try(var body = new PrintWriter(bodyBuilder)) {
            body.println("public static %s %s(byte[] bytes) {".formatted(typeDeclaration.getCanonicalName(), methodName));
            var requiredFields = getRequiredFields(fields);
            if(!requiredFields.isEmpty()){
                body.println("java.util.BitSet fields = new java.util.BitSet();");
            }
            body.println("%s input = new %s(bytes);".formatted(ARRAY_INPUT_STREAM, ARRAY_INPUT_STREAM));
            fields.forEach((field, annotation) -> createDeserializerField(field, annotation, body));
            body.println("while (true) {");
            body.println("int rawTag = input.readTag();");
            body.println("if (rawTag == 0) break;");
            body.println("int index = rawTag >>> 3;");
            body.println("int tag = rawTag & 7;");
            if(!requiredFields.isEmpty()) {
                body.println("fields.set(index);");
            }
            body.println("switch(index) {");
            fields.forEach((field, annotation) -> {
                if(annotation.ignore()){
                    return;
                }

                var converter = getConverter(field, annotation, true);
                createDeserializerField(typeDeclaration, body, field, annotation, converter);
            });
            body.println("default:");
            body.println("input.readBytes();");
            body.println("break;");
            body.println("}");
            body.println("}");
            requiredFields.forEach((field, property) -> {
                body.println("if(!fields.get(%s))".formatted(property.index()));
                body.println("throw %s.missingMandatoryField(\"%s\");".formatted(PROTOBUF_DESERIALIZATION_EXCEPTION,  field.getName()));
            });
            var args = getConstructorCallArgs(fields);
            body.println("return new %s(%s);".formatted(typeDeclaration.getCanonicalName(), args));
            body.println("}");
            addSafeMethod(typeDeclaration, methodName, bodyBuilder.toString(), byte[].class);
            getLog().info("Created deserialization method");
        }
    }

    private String getConstructorCallArgs(Map<FieldDescription.InDefinedShape, ProtobufProperty> fields) {
        return fields.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().ignore())
                .map(entry -> entry.getValue().repeated() ? "%sValues".formatted(entry.getKey().getName()) : entry.getKey().getName())
                .collect(Collectors.joining(", "));
    }

    private Map<FieldDescription.InDefinedShape, ProtobufProperty> getRequiredFields(Map<FieldDescription.InDefinedShape, ProtobufProperty> fields) {
        return fields.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().ignore() && entry.getValue().required())
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (first, second) -> first, LinkedHashMap::new));
    }

    private void createDeserializerField(FieldDescription.InDefinedShape field, ProtobufProperty annotation, PrintWriter body) {
        if(annotation.ignore()){
            return;
        }

        if(annotation.repeated()){
            var implementationName = getCollectionType(field);
            body.println("%s %sValues = new %s();".formatted(implementationName, field.getName(), implementationName));
            return;
        }

        if(!field.getType().isPrimitive()){
            body.println("%s %s = null;".formatted(field.getType().getActualName(), field.getName()));
            return;
        }

        var defaultValue = switch (annotation.type()){
            case FLOAT -> "0F";
            case DOUBLE -> "0D";
            case BOOL -> "false";
            case STRING, BYTES -> "null";
            case INT32, SINT32, UINT32, FIXED32, SFIXED32 -> "0";
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> "0L";
            default -> throw new IllegalStateException("Unexpected value: " + annotation.type());
        };
        body.println("%s %s = %s;".formatted(field.getType().getActualName(), field.getName(), defaultValue));
    }

    private String getCollectionType(FieldDescription.InDefinedShape field) {
        var type = field.getType().asErasure();
        return type.isAssignableFrom(Collection.class) || type.isAssignableFrom(Map.class)
                ? ArrayList.class.getName()
                : type.getActualName();
    }

    private void addSafeMethod(TypeDescription typeDeclaration, String methodName, String body, Class<?>... params) {
        findMethod(typeDeclaration, methodName, params)
                .ifPresent(typeDeclaration.getDeclaredMethods()::remove);
        // typeDeclaration.getDeclaredMethods().add(StaticJavaParser.parseMethodDescription.InDefinedShape(body));
    }

    private String toJavaType(MethodDescription.InDefinedShape methodDeclaration){
        return methodDeclaration.getDeclaringType().getCanonicalName() + "." + methodDeclaration.getName();
    }

    private void createDeserializerField(TypeDescription typeDeclaration, PrintWriter body, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter) {
        body.println("case %s: ".formatted(annotation.index()));
        switch (annotation.type()){
            case MESSAGE -> createDeserializerMessage(typeDeclaration, field, annotation, converter, body);
            case BOOL -> createDeserializerBoolean(body, field, annotation, converter);
            case STRING -> createDeserializerString(body, field, annotation, converter);
            case BYTES -> createDeserializerBytes(body, field, annotation, converter);
            case FLOAT, DOUBLE, INT32, SINT32, UINT32, FIXED32, SFIXED32, FIXED64, SFIXED64, INT64, SINT64, UINT64 -> createDeserializerScalar(body, field, annotation, converter);
        }
    }

    private void createDeserializerScalar(PrintWriter body, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter) {
        var methodName = getScalarMethodName(annotation);
        var expectedTag = getScalarWireType(annotation);
        if(annotation.repeated()){
            body.println("switch(tag) {");
            body.println("case %s:".formatted(WIRE_TYPE_LENGTH_DELIMITED));
            if(converter != null){
                body.println("%sValues.addAll(%s(input.read%sPacked()));".formatted(field.getName(), toJavaType(converter), methodName));
            }else {
                body.println("%sValues.addAll(input.read%sPacked());".formatted(field.getName(), methodName));
            }
            body.println("break;");
            body.println("case %s:".formatted(expectedTag));
            writeDeserializerFixedScalar(body, field, annotation, converter, methodName);
            body.println("default: throw %s.invalidTag(tag);".formatted(PROTOBUF_DESERIALIZATION_EXCEPTION));
            body.println("}");
            body.println("break;");
            return;
        }

        body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(expectedTag, PROTOBUF_DESERIALIZATION_EXCEPTION));
        writeDeserializerFixedScalar(body, field, annotation, converter, methodName);
    }

    private int getScalarWireType(ProtobufProperty annotation) {
        return switch (annotation.type()) {
            case FLOAT, FIXED32, SFIXED32 -> WIRE_TYPE_FIXED32;
            case DOUBLE, FIXED64, SFIXED64 -> WIRE_TYPE_FIXED64;
            case INT32, SINT32, UINT32, INT64, SINT64, UINT64 -> WIRE_TYPE_VAR_INT;
            default -> throw new IllegalStateException("Unexpected scalar type: " + annotation.type());
        };
    }

    private String getScalarMethodName(ProtobufProperty annotation) {
        return switch (annotation.type()) {
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            case INT32, SINT32, UINT32 -> "Int32";
            case FIXED32, SFIXED32 -> "Fixed32";
            case INT64, SINT64, UINT64 -> "Int64";
            case FIXED64, SFIXED64 -> "Fixed64";
            default -> throw new IllegalStateException("Unexpected scalar type: " + annotation.type());
        };
    }

    private void writeDeserializerFixedScalar(PrintWriter body, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter, String methodName) {
        if(converter != null && annotation.repeated()){
            body.println("%sValues.add(%s.valueOf(%s(input.read%s())));".formatted(field.getName(), annotation.type().wrappedType().getName(), toJavaType(converter), methodName));
            body.println("break;");
            return;
        }

        if(converter != null){
            body.println("%s = %s(input.read%s());".formatted(field.getName(), toJavaType(converter), methodName));
            body.println("break;");
            return;
        }

        if(annotation.repeated()){
            body.println("%sValues.add(%s.valueOf(input.read%s()));".formatted(field.getName(), annotation.type().wrappedType().getName(), methodName));
            body.println("break;");
            return;
        }

        if(field.getType().isPrimitive()) {
            body.println("%s = input.read%s();".formatted(field.getName(), methodName));
            body.println("break;");
            return;
        }

        body.println("%s = %s.valueOf(input.read%s());".formatted(field.getName(), annotation.type().wrappedType().getName(), methodName));
        body.println("break;");
    }

    private void createDeserializerBytes(PrintWriter body, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter) {
        body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_LENGTH_DELIMITED , PROTOBUF_DESERIALIZATION_EXCEPTION));
        if(converter != null && annotation.repeated()) {
            var converterName = toJavaType(converter);
            body.println("%sValues.add(%s(input.readBytes()));".formatted(field.getName(), converterName));
            body.println("break;");
            return;
        }

        if(converter != null) {
            var converterName = toJavaType(converter);
            body.println("%s = %s(input.readBytes());".formatted(field.getName(), converterName));
            body.println("break;");
            return;
        }

        if(annotation.repeated()){
            body.println("%sValues.add(input.readBytes());".formatted(field.getName()));
            body.println("break;");
            return;
        }

        body.println("%s = input.readBytes();".formatted(field.getName()));
        body.println("break;");
    }

    private void createDeserializerString(PrintWriter body, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter) {
        body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_LENGTH_DELIMITED, PROTOBUF_DESERIALIZATION_EXCEPTION));
        if(converter != null && annotation.repeated()) {
            var converterName = toJavaType(converter);
            body.println("%sValues.add(%s(new String(input.readBytes(), java.nio.charset.StandardCharsets.UTF_8)));".formatted(field.getName(), converterName));
            body.println("break;");
            return;
        }

        if(converter != null) {
            var converterName = toJavaType(converter);
            body.println("%s = %s(new String(input.readBytes(), java.nio.charset.StandardCharsets.UTF_8));".formatted(field.getName(), converterName));
            body.println("break;");
            return;
        }

        if(annotation.repeated()){
            body.println("%sValues.add(new String(input.readBytes(), java.nio.charset.StandardCharsets.UTF_8));".formatted(field.getName()));
            body.println("break;");
            return;
        }

        body.println("%s = new String(input.readBytes(), java.nio.charset.StandardCharsets.UTF_8);".formatted(field.getName()));
        body.println("break;");
    }

    private void createDeserializerMessage(TypeDescription typeDeclaration, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter, PrintWriter body) {
        var implementationType = getImplementationType(field, annotation).orElse(null);
        if(implementationType == null){
            return;
        }

        var deserializationMethod = getDeserializationMethod(implementationType);
        processClass(typeDeclaration);
        addMessageTagCheck(body, implementationType);
        var readMethod = implementationType.isEnum() ? "Int32" : "Bytes";
        var implementation = implementationType.getCanonicalName();
        if(converter != null && annotation.repeated()) {
            var converterName = toJavaType(converter);
            body.println("%sValues.add(%s(%s.%s(input.read%s())));".formatted(field.getName(), converterName, implementation, deserializationMethod, readMethod));
            body.println("break;");
            return;
        }

        if(converter != null) {
            var converterName = toJavaType(converter);
            body.println("%s = %s(%s.%s(input.read%s()));".formatted(field.getName(), converterName, implementation, deserializationMethod, readMethod));
            body.println("break;");
            return;
        }

        if(annotation.repeated()){
            body.println("%sValues.add(%s.%s(input.read%s()));".formatted(field.getName(), implementation, deserializationMethod, readMethod));
            body.println("break;");
            return;
        }

        body.println("%s = %s.%s(input.read%s());".formatted(field.getName(), implementation, deserializationMethod, readMethod));
        body.println("break;");
    }

    private void addMessageTagCheck(PrintWriter body, TypeDescription implementationType) {
        if(implementationType.isEnum()){
            body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_VAR_INT, PROTOBUF_DESERIALIZATION_EXCEPTION));
            return;
        }

        body.println("if(tag != %s && tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_LENGTH_DELIMITED, WIRE_TYPE_EMBEDDED_MESSAGE, PROTOBUF_DESERIALIZATION_EXCEPTION));
    }

    private String getDeserializationMethod(TypeDescription implementationType) {
        return implementationType.isEnum() ? DESERIALIZATION_ENUM_METHOD : DESERIALIZATION_CLASS_METHOD;
    }

    private void createDeserializerBoolean(PrintWriter body, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter) {
        if (!annotation.repeated()) {
            body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_VAR_INT, PROTOBUF_DESERIALIZATION_EXCEPTION));
            writeDeserializerFixedBoolean(body, field, annotation, converter);
            return;
        }
        body.println("switch(tag) {");
        body.println("case %s:".formatted(WIRE_TYPE_LENGTH_DELIMITED));
        if (converter != null) {
            body.println("%sValues.addAll(%s(input.readBoolPacked()));".formatted(field.getName(), toJavaType(converter)));
        } else {
            body.println("%sValues.addAll(input.readBoolPacked());".formatted(field.getName()));
        }
        body.println("break;");
        body.println("case %s:".formatted(WIRE_TYPE_VAR_INT));
        writeDeserializerFixedBoolean(body, field, annotation, converter);
        body.println("default: throw %s.invalidTag(tag);".formatted(PROTOBUF_DESERIALIZATION_EXCEPTION));
        body.println("}");
        body.println("break;");
    }

    private void writeDeserializerFixedBoolean(PrintWriter body, FieldDescription.InDefinedShape field, ProtobufProperty annotation, MethodDescription.InDefinedShape converter) {
        if(converter != null && annotation.repeated()) {
            body.println("%sValues.add(%s(input.readBool()));".formatted(field.getName(), toJavaType(converter)));
            body.println("break;");
            return;
        }

        if(converter != null && field.getType().isPrimitive()) {
            body.println("%s = %s(input.readBool());".formatted(field.getName(), toJavaType(converter)));
            body.println("break;");
            return;
        }

        if(converter != null) {
            body.println("%s = Boolean.valueOf(%s(input.readBool()));".formatted(field.getName(), toJavaType(converter)));
            body.println("break;");
            return;
        }

        if(annotation.repeated()){
            body.println("%sValues.add(input.readBool());".formatted(field.getName()));
            body.println("break;");
            return;
        }

        if(field.getType().isPrimitive()) {
            body.println("%s = input.readBool();".formatted(field.getName()));
            body.println("break;");
            return;
        }

        body.println("%s = Boolean.valueOf(input.readBool());".formatted(field.getName()));
        body.println("break;");
    }

    private Map<FieldDescription.InDefinedShape, ProtobufProperty> getProtobufFields(TypeDescription typeDeclaration) {
        return typeDeclaration.getDeclaredFields()
                .stream()
                .map(entry -> getAnnotation(entry, ProtobufProperty.class).map(value -> Map.entry(entry, value)))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
    }

    private URLClassLoader createClassLoader(boolean test) {
        var projectFiles = getProjectFiles(test);
        var projectUrl = parseURL(projectFiles);
        return URLClassLoader.newInstance(new URL[]{projectUrl});
    }

    private String getProjectFiles(boolean test) {
       return test ? project.getBuild().getTestOutputDirectory() : project.getBuild().getOutputDirectory();
    }

    private URL parseURL(String resource) {
        try {
            return new File(resource).toURI().toURL();
        } catch (MalformedURLException exception) {
            throw new RuntimeException("Cannot parse URL", exception);
        }
    }

    private List<String> getAllClasses(URLClassLoader pluginClassLoader) {
        var scanner = new ClassGraph()
                .overrideClassLoaders(pluginClassLoader)
                .enableClassInfo();
        try(var scanResult = scanner.scan()) {
            return scanResult.getClassesImplementing(ProtobufMessage.class)
                    .stream()
                    .map(ClassInfo::getName)
                    .toList();
        }
    }
    
    private Optional<TypeDescription> getFromClassPool(String name) {
        var result = classPool.describe(name);
        if(!result.isResolved()){
            return Optional.empty();
        }
        
        return Optional.of(result.resolve());
    }
    
    private <T extends Annotation> Optional<T> getAnnotation(AnnotationSource source, Class<T> type){
        return Optional.ofNullable(source.getDeclaredAnnotations().ofType(type))
                .map(AnnotationDescription.Loadable::load);
    }
}
