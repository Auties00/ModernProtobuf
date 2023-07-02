package it.auties.protobuf.serialization;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.resolution.types.ResolvedType;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import it.auties.protobuf.base.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import static it.auties.protobuf.base.ProtobufMessage.*;
import static it.auties.protobuf.base.ProtobufWireType.*;

@Mojo(name = "protobuf", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@SuppressWarnings("unused")
public class ProtobufMavenMojo extends AbstractMojo {
    private static final ResolvedType COLLECTION_TYPE = StaticJavaParser.parseType(Collection.class.getName())
            .resolve();
    private static final ResolvedType MAP_TYPE = StaticJavaParser.parseType(Map.class.getName())
            .resolve();
    private static final String ARRAY_INPUT_STREAM = ProtobufInputStream.class.getName();
    private static final String ARRAY_OUTPUT_STREAM = ProtobufOutputStream.class.getName();
    private static final String PROTOBUF_SERIALIZATION_EXCEPTION = ProtobufSerializationException.class.getName();
    private static final String PROTOBUF_DESERIALIZATION_EXCEPTION = ProtobufDeserializationException.class.getName();
    private static final String SERIALIZATION_STUB = """
            public byte[] %s() {
                throw new UnsupportedOperationException();
            }
            """;
    private static final String DESERIALIZATION_STUB = """
            public static %s %s(byte[] bytes) {
                throw new UnsupportedOperationException();
            }
            """;
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

    @org.apache.maven.plugins.annotations.Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    private final Set<String> processed = new HashSet<>();
    
    private Map<String, TypeDeclaration<?>> classPool;

    @Override
    public void execute() throws MojoExecutionException {
        runTransformation(false);
        runTransformation(true);
    }

    private void runTransformation(boolean test) throws MojoExecutionException {
        var time = System.currentTimeMillis();
        try {
            getLog().info("Starting to process %s".formatted(test ? "tests" : "files"));
            this.classPool = createClassPool(new File(test ? project.getBuild().getTestOutputDirectory() : project.getBuild().getOutputDirectory()));
            getLog().info("Found %s message%s to process".formatted(classPool.size(), classPool.size() > 1 ? "s" : ""));
            classPool.forEach((name, typeDeclaration) -> processClass(typeDeclaration, test));
        } catch (Throwable exception) {
            getLog().error(exception);
            throw new MojoExecutionException("Cannot run protobuf plugin", exception);
        }finally {
            getLog().info("Processing took %sms".formatted(System.currentTimeMillis() - time));
        }
    }

    private void processClass(TypeDeclaration<?> typeDeclaration, boolean test) {
        var className = typeDeclaration.getNameAsString();
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
        createSerializationMethod(typeDeclaration, fields, test);
        createDeserializerMethod(typeDeclaration, fields, test);
        // TODO: Write result
    }

    private boolean isNonConcrete(TypeDeclaration<?> typeDeclaration) {
        return typeDeclaration.toClassOrInterfaceDeclaration()
                .map(ClassOrInterfaceDeclaration::isInterface)
                .orElse(typeDeclaration.hasModifier(Keyword.ABSTRACT));
    }
    
    private void createSerializationMethod(TypeDeclaration<?> typeDeclaration, Map<VariableDeclarator, ProtobufProperty> fields, boolean test) {
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

                var converter = getConverter(typeDeclaration, field, annotation, false);
                createSerializationField(typeDeclaration, body, field, annotation, converter, test);
            }
            body.println("return output.toByteArray();");
            body.println("}");
            addSafeMethod(typeDeclaration, SERIALIZATION_METHOD, bodyBuilder.toString());
            getLog().info("Created serialization method");
        }
    }

    private MethodDeclaration getConverter(TypeDeclaration<?> typeDeclaration, VariableDeclarator field, ProtobufProperty annotation, boolean staticMethod) {
        var ctType = getImplementationType(typeDeclaration, field, annotation);
        var ctPrimitive = classPool.get(annotation.type().primitiveType().getName());
        var declaration = classPool.get(annotation.type().wrappedType().getName());
        if (ctType.isDescendantOf(ctPrimitive) || isSubType(ctType, declaration)) {
            return null;
        }

        return ctType.getMethods()
                .stream()
                .filter(entry -> entry.getAnnotationByClass(ProtobufConverter.class).isPresent() && staticMethod == entry.isStatic())
                .findFirst()
                .orElseThrow(() -> new ProtobufSerializationException("Missing converter in %s".formatted(ctType.getName())));
    }

    private boolean isSubType(TypeDeclaration<?> first, TypeDeclaration<?> second){
        return first instanceof NodeWithType<?, ?> firstTyped && second instanceof NodeWithType<?, ?> secondTyped
                && secondTyped.getType().resolve().isAssignableBy(firstTyped.getType().resolve());
    }

    private TypeDeclaration<?> getImplementationType(TypeDeclaration<?> typeDeclaration, VariableDeclarator field, ProtobufProperty annotation) {
        try {
            var result = classPool.get(annotation.implementation().getName());
            return getConvertedWrapperType(typeDeclaration, field, annotation, result);
        }catch (UndeclaredThrowableException exception){
            var result = classPool.get(exception.getCause().getMessage());
            return getConvertedWrapperType(typeDeclaration, field, annotation, result);
        }
    }

    private TypeDeclaration<?> getConvertedWrapperType(TypeDeclaration<?> typeDeclaration, VariableDeclarator field, ProtobufProperty annotation, TypeDeclaration<?> implementation) {
        if (!Objects.equals(implementation.getNameAsString(), ProtobufMessage.class.getName())) {
            return implementation;
        }

        if (annotation.repeated()) {
            return classPool.get(annotation.type().wrappedType().getName());
        }

        return classPool.get(field.getTypeAsString());
    }

    private void createSerializationField(TypeDeclaration<?> typeDeclaration, PrintWriter body, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter, boolean test) {
        var nullCheck = !field.getType().isPrimitiveType() && annotation.required();
        if(nullCheck) {
            body.println("if(%s == null) {".formatted(field.getName()));
            body.println("throw %s.missingMandatoryField(\"%s\");".formatted(PROTOBUF_SERIALIZATION_EXCEPTION,  field.getName()));
            body.println("}");
        }

        switch (annotation.type()){
            case MESSAGE -> createSerializationMessage(typeDeclaration, body, field, annotation, converter, test, nullCheck);
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

    private void createSerializationMessage(TypeDeclaration<?> typeDeclaration, PrintWriter body, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter, boolean test, boolean nullCheck) {
        var ctType = getImplementationType(typeDeclaration, field, annotation);
        processClass(typeDeclaration, test);
        if(!annotation.repeated()) {
            if (nullCheck) {
                body.println("else {");
            } else {
                body.println("if(%s != null) {".formatted(field.getName()));
            }
        }

        if(ctType instanceof EnumDeclaration){
            if(annotation.repeated()) {
                createSerializationRepeatedFixed(typeDeclaration, field, annotation, converter, body, nullCheck);
                body.println("output.writeUInt32(%s, entry%s.index());".formatted(annotation.index(), toMethodCall(converter)));
                body.println("}");
            }else {
                var getter = findGetter(typeDeclaration, field, annotation);
                body.println("output.writeUInt32(%s, %s%s.index());".formatted(annotation.index(), getter, toMethodCall(converter)));
            }
        }else if(annotation.repeated()){
            createSerializationRepeatedFixed(typeDeclaration, field, annotation, converter, body, nullCheck);
            body.println("output.writeBytes(%s, entry%s.%s());".formatted(annotation.index(), toMethodCall(converter), SERIALIZATION_METHOD));
            body.println("}");
        }else {
            var getter = findGetter(typeDeclaration, field, annotation);
            body.println("output.writeBytes(%s, %s%s.%s());".formatted(annotation.index(), getter, toMethodCall(converter), SERIALIZATION_METHOD));
        }

        body.println("}");
    }

    private void createSerializationAny(TypeDeclaration<?> typeDeclaration, PrintWriter body, ProtobufProperty annotation, MethodDeclaration converter, String writeType, VariableDeclarator field, boolean nullCheck) {
        if(annotation.repeated()){
            createSerializationRepeatedFixed(typeDeclaration, field, annotation, converter, body, nullCheck);
            body.println("output.write%s(%s, entry%s);".formatted(writeType, annotation.index(), toMethodCall(converter)));
            body.println("}");
            body.println("}");
            return;
        }

        var primitive = field.getType().isPrimitiveType();
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

    private void createSerializationRepeatedFixed(TypeDeclaration<?> typeDeclaration, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter, PrintWriter body, boolean nullCheck) {
        var implementationType = getImplementationType(typeDeclaration, field, annotation).getName();
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

    private String toMethodCall(MethodDeclaration converter) {
        return converter != null ? ".%s()".formatted(converter.getName()) : "";
    }

    private String findGetter(TypeDeclaration<?> typeDeclaration, VariableDeclarator variable, ProtobufProperty annotation){
        if (!annotation.repeated()) {
            return variable.getNameAsString();
        }

        if(COLLECTION_TYPE.isAssignableBy(variable.getType().resolve())){
            return variable.getNameAsString();
        }

        var accessor = findMethod(typeDeclaration, variable.getNameAsString())
                .or(() -> findMethod(typeDeclaration, "get" + variable.getNameAsString().substring(0, 1).toUpperCase() + variable.getNameAsString().substring(1)))
                .orElseThrow(() -> new NoSuchElementException("Missing getter/accessor for repeated field %s in %s".formatted(variable.getName(), typeDeclaration.getNameAsString())));
        if(!COLLECTION_TYPE.isAssignableBy(accessor.getType().resolve())){
            throw new IllegalStateException("Unexpected getter/accessor for repeated field %s in %s: expected return type to extend Collection".formatted(variable.getName(), typeDeclaration.getName()));
        }

        return accessor.getName() + "()";
    }

    private Optional<MethodDeclaration> findMethod(TypeDeclaration<?> typeDeclaration, String name, String... params){
        return typeDeclaration.getMethodsBySignature(name, params)
                .stream()
                .findFirst();
    }

    private void createDeserializerMethod(TypeDeclaration<?> typeDeclaration, Map<VariableDeclarator, ProtobufProperty> fields, boolean test) {
        var methodName = getDeserializationMethod(typeDeclaration);
        if(typeDeclaration instanceof EnumDeclaration){
            var methodBody = DESERIALIZATION_ENUM_BODY.formatted(typeDeclaration.getName(), methodName, typeDeclaration.getName(), typeDeclaration.getName());
            addSafeMethod(typeDeclaration, methodName, methodBody, "int");
            return;
        }

        var builderMethod = findMethod(typeDeclaration, "builder");
        if(builderMethod.isEmpty()){
            getLog().error("Missing builder() in %s".formatted(typeDeclaration.getName()));
            return;
        }

        var bodyBuilder = new StringWriter();
        try(var body = new PrintWriter(bodyBuilder)) {
            body.println("public static %s %s(byte[] bytes) {".formatted(toJavaType(typeDeclaration), methodName));
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

                var converter = getConverter(typeDeclaration, field, annotation, true);
                createDeserializerField(typeDeclaration, body, field, annotation, converter, test);
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
            body.println("return new %s(%s);".formatted(toJavaType(typeDeclaration), args));
            body.println("}");
            addSafeMethod(typeDeclaration, methodName, bodyBuilder.toString(), "byte[]");
            getLog().info("Created deserialization method");
        }
    }

    private String getConstructorCallArgs(Map<VariableDeclarator, ProtobufProperty> fields) {
        return fields.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().ignore())
                .map(entry -> entry.getValue().repeated() ? "%sValues".formatted(entry.getKey().getNameAsString()) : entry.getKey().getNameAsString())
                .collect(Collectors.joining(", "));
    }

    private Map<VariableDeclarator, ProtobufProperty> getRequiredFields(Map<VariableDeclarator, ProtobufProperty> fields) {
        return fields.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().ignore() && entry.getValue().required())
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (first, second) -> first, LinkedHashMap::new));
    }

    private void createDeserializerField(VariableDeclarator field, ProtobufProperty annotation, PrintWriter body) {
        if(annotation.ignore()){
            return;
        }

        if(annotation.repeated()){
            var implementationName = getCollectionType(field);
            body.println("%s %sValues = new %s();".formatted(implementationName, field.getName(), implementationName));
            return;
        }

        if(!field.getType().isPrimitiveType()){
            body.println("%s %s = null;".formatted(field.getTypeAsString(), field.getName()));
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
        body.println("%s %s = %s;".formatted(field.getTypeAsString(), field.getName(), defaultValue));
    }

    private String getCollectionType(VariableDeclarator field) {
        var type = field.getType().resolve();
        return COLLECTION_TYPE.isAssignableBy(type) || MAP_TYPE.isAssignableBy(type) ? ArrayList.class.getName()
                : field.getTypeAsString();
    }

    private void addSafeMethod(TypeDeclaration<?> typeDeclaration, String methodName, String body, String... params) {
        findMethod(typeDeclaration, methodName, params)
                .ifPresent(typeDeclaration::remove);
        typeDeclaration.addMember(StaticJavaParser.parseMethodDeclaration(body));
    }

    private String toJavaType(MethodDeclaration methodDeclaration){
        var parent = methodDeclaration.getParentNode()
                .filter(entry -> entry instanceof NodeWithName<?>)
                .map(entry -> (NodeWithName<?>) entry)
                .orElseThrow();
        return toJavaType(parent.getNameAsString() + "." + methodDeclaration.getNameAsString());
    }

    private String toJavaType(TypeDeclaration<?> declaration){
        return toJavaType(declaration.getFullyQualifiedName().orElse(declaration.getNameAsString()));
    }

    private String toJavaType(String type){
        return type.replaceAll("\\$", ".");
    }

    private void createDeserializerField(TypeDeclaration<?> typeDeclaration, PrintWriter body, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter, boolean test) {
        body.println("case %s: ".formatted(annotation.index()));
        switch (annotation.type()){
            case MESSAGE -> createDeserializerMessage(typeDeclaration, field, annotation, converter, body, test);
            case BOOL -> createDeserializerBoolean(body, field, annotation, converter);
            case STRING -> createDeserializerString(body, field, annotation, converter);
            case BYTES -> createDeserializerBytes(body, field, annotation, converter);
            case FLOAT, DOUBLE, INT32, SINT32, UINT32, FIXED32, SFIXED32, FIXED64, SFIXED64, INT64, SINT64, UINT64 -> createDeserializerScalar(body, field, annotation, converter);
        }
    }

    private void createDeserializerScalar(PrintWriter body, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter) {
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

    private void writeDeserializerFixedScalar(PrintWriter body, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter, String methodName) {
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

        if(field.getType().isPrimitiveType()) {
            body.println("%s = input.read%s();".formatted(field.getName(), methodName));
            body.println("break;");
            return;
        }

        body.println("%s = %s.valueOf(input.read%s());".formatted(field.getName(), annotation.type().wrappedType().getName(), methodName));
        body.println("break;");
    }

    private void createDeserializerBytes(PrintWriter body, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter) {
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

    private void createDeserializerString(PrintWriter body, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter) {
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

    private void createDeserializerMessage(TypeDeclaration<?> typeDeclaration, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter, PrintWriter body, boolean test) {
        var implementationType = getImplementationType(typeDeclaration, field, annotation);
        var deserializationMethod = getDeserializationMethod(implementationType);
        processClass(typeDeclaration, test);
        addMessageTagCheck(body, implementationType);
        var readMethod = implementationType instanceof EnumDeclaration ? "Int32" : "Bytes";
        var implementation = toJavaType(implementationType);
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

    private void addMessageTagCheck(PrintWriter body, TypeDeclaration<?> implementationType) {
        if(implementationType instanceof EnumDeclaration){
            body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_VAR_INT, PROTOBUF_DESERIALIZATION_EXCEPTION));
            return;
        }

        body.println("if(tag != %s && tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_LENGTH_DELIMITED, WIRE_TYPE_EMBEDDED_MESSAGE, PROTOBUF_DESERIALIZATION_EXCEPTION));
    }

    private String getDeserializationMethod(TypeDeclaration<?> implementationType) {
        return implementationType instanceof EnumDeclaration ? DESERIALIZATION_ENUM_METHOD : DESERIALIZATION_CLASS_METHOD;
    }

    private void createDeserializerBoolean(PrintWriter body, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter) {
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

    private void writeDeserializerFixedBoolean(PrintWriter body, VariableDeclarator field, ProtobufProperty annotation, MethodDeclaration converter) {
        if(converter != null && annotation.repeated()) {
            body.println("%sValues.add(%s(input.readBool()));".formatted(field.getName(), toJavaType(converter)));
            body.println("break;");
            return;
        }

        if(converter != null && field.getType().isPrimitiveType()) {
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

        if(field.getType().isPrimitiveType()) {
            body.println("%s = input.readBool();".formatted(field.getName()));
            body.println("break;");
            return;
        }

        body.println("%s = Boolean.valueOf(input.readBool());".formatted(field.getName()));
        body.println("break;");
    }

    private <T extends TypeDeclaration<T>> Map<VariableDeclarator, ProtobufProperty> getProtobufFields(TypeDeclaration<T> typeDeclaration) {
        if(typeDeclaration instanceof RecordDeclaration recordDeclaration) {
            return recordDeclaration.getParameters()
                    .stream()
                    .map(this::getProtobufPropertyEntry)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
        }

        if(typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration){
            return classOrInterfaceDeclaration.getFields()
                    .stream()
                    .map(this::getProtobufPropertyEntry)
                    .reduce(new LinkedHashMap<>(), (first, second) -> { first.putAll(second); return first; });
        }
        
        throw new IllegalArgumentException("Unknown class type: " + classPool.getClass().getName());
    }

    private Optional<Entry<ParameterVar, ProtobufProperty>> getProtobufPropertyEntry(Parameter parameter) {
        return parameter.getAnnotationByClass(ProtobufProperty.class)
                .map(this::createMockAnnotation)
                .map(property -> Map.entry(new ParameterVar(parameter), property));
    }

    private <T extends Node> LinkedHashMap<VariableDeclarator, ProtobufProperty> getProtobufPropertyEntry(FieldDeclaration entry) {
        return entry.getAnnotationByClass(ProtobufProperty.class)
                .map(this::createMockAnnotation)
                .map(property -> getProtobufPropertyEntry(entry, property))
                .orElseGet(LinkedHashMap::new);
    }

    private LinkedHashMap<VariableDeclarator, ProtobufProperty> getProtobufPropertyEntry(FieldDeclaration entry, ProtobufProperty property) {
        return entry.getVariables()
                .stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> property, (first, second) -> first, LinkedHashMap::new));
    }

    private static class ParameterVar extends VariableDeclarator {
        private ParameterVar(Parameter parameter){
            super(parameter.getType(), parameter.getName());
        }
    }

    private ProtobufProperty createMockAnnotation(AnnotationExpr annotationExpr) {
        if(!(annotationExpr instanceof NormalAnnotationExpr annotation)){
            throw new IllegalArgumentException("Illegal statement: expected @ProtobufProperty to have a body");
        }

        var pairs = annotation.getPairs()
                .stream()
                .collect(Collectors.toUnmodifiableMap(NodeWithSimpleName::getNameAsString, MemberValuePair::getValue));
        return new ProtobufProperty() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ProtobufProperty.class;
            }

            @Override
            public int index() {
                var literalExpression =  pairs.get("index");
                if(!(literalExpression instanceof IntegerLiteralExpr integerLiteralExpr)){
                    throw new IllegalArgumentException("Illegal statement: expected @ProtobufProperty's index to be an int literal");
                }

                return integerLiteralExpr.asNumber().intValue();
            }

            @Override
            public ProtobufType type() {
                var literalExpression =  pairs.get("type");
                var result = literalExpression.toString();
                var index = result.lastIndexOf(".");
                return ProtobufType.of(result.substring(index + 1))
                            .orElseThrow(() -> new IllegalArgumentException("Illegal statement, invalid type: " + result));
            }

            @Override
            public Class<?> implementation() {
                var implementation = pairs.get("implementation");
                if(implementation == null){
                    return null;
                }

                //noinspection SuspiciousInvocationHandlerImplementation
                return (Class<?>) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class[]{Class.class}, (proxy, ignored, args) -> implementation.toString());
            }

            @Override
            public boolean required() {
                var literalExpression =  pairs.get("required");
                if(!(literalExpression instanceof BooleanLiteralExpr booleanLiteralExpr)){
                    throw new IllegalArgumentException("Illegal statement: expected @ProtobufProperty's index to be a boolean literal");
                }

                return booleanLiteralExpr.getValue();
            }

            @Override
            public boolean ignore() {
                var literalExpression =  pairs.get("ignore");
                if(!(literalExpression instanceof BooleanLiteralExpr booleanLiteralExpr)){
                    throw new IllegalArgumentException("Illegal statement: expected @ProtobufProperty's index to be a boolean literal");
                }

                return booleanLiteralExpr.getValue();
            }

            @Override
            public boolean repeated() {
                var literalExpression =  pairs.get("repeated");
                if(!(literalExpression instanceof BooleanLiteralExpr booleanLiteralExpr)){
                    throw new IllegalArgumentException("Illegal statement: expected @ProtobufProperty's index to be a boolean literal");
                }

                return booleanLiteralExpr.getValue();
            }
        };
    }

    private List<ClassInfo> getAllClasses(URLClassLoader pluginClassLoader) {
        var scanner = new ClassGraph()
                .overrideClassLoaders(pluginClassLoader)
                .enableClassInfo();
        try(var scanResult = scanner.scan()) {
            return scanResult.getClassesImplementing(ProtobufMessage.class);
        }
    }

    private URL parseURL(String resource) {
        try {
            return new File(resource).toURI().toURL();
        } catch (MalformedURLException exception) {
            throw new RuntimeException("Cannot parse URL", exception);
        }
    }

    private Map<String, TypeDeclaration<?>> createClassPool(File directory) {
        if(directory == null){
            return Map.of();
        }

        try(var walker = Files.walk(directory.toPath())) {
            return walker.filter(entry -> entry.endsWith(".java"))
                    .map(this::parseClass)
                    .flatMap(Optional::stream)
                    .map(CompilationUnit::getTypes)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toUnmodifiableMap(entry -> entry.getFullyQualifiedName().orElse(entry.getNameAsString()), Function.identity()));
        }catch (IOException exception){
            throw new RuntimeException("Cannot create class pool", exception);
        }
   }

    private Optional<CompilationUnit> parseClass(Path path) {
        try {
            var parser = new JavaParser();
            parser.getParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_17);
            var result = parser.parse(path);
            return result.getResult();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
