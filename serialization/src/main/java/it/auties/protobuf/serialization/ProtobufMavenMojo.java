package it.auties.protobuf.serialization;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import it.auties.protobuf.base.*;
import javassist.*;
import javassist.compiler.Javac;
import lombok.SneakyThrows;
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
import java.lang.reflect.UndeclaredThrowableException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.auties.protobuf.base.ProtobufWireType.*;

@Mojo(name = "protobuf", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
@SuppressWarnings("unused")
public class ProtobufMavenMojo extends AbstractMojo {
    private static final String SERIALIZATION_METHOD = "toEncodedProtobuf";
    private static final String DESERIALIZATION_CLASS_METHOD = "ofProtobuf";
    private static final String DESERIALIZATION_ENUM_METHOD = "of";
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

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    private final Set<String> processed = new HashSet<>();

    @Override
    public void execute() throws MojoExecutionException {
        runTransformation(false);
        runTransformation(true);
    }

    private void runTransformation(boolean test) throws MojoExecutionException {
        var time = System.currentTimeMillis();
        try(var pluginClassLoader = URLClassLoader.newInstance(new URL[]{parseURL(test ? project.getBuild().getTestOutputDirectory() : project.getBuild().getOutputDirectory())})) {
            getLog().info("Starting to process %s".formatted(test ? "tests" : "files"));
            var classPool = new ClassPool(ClassPool.getDefault());
            classPool.childFirstLookup = true;
            classPool.appendClassPath(test ? project.getBuild().getTestOutputDirectory() : project.getBuild().getOutputDirectory());
            classPool.appendClassPath(new LoaderClassPath(pluginClassLoader));
            classPool.appendSystemPath();
            var classes = getAllClasses(pluginClassLoader);
            getLog().info("Found %s message%s to process".formatted(classes.size(), classes.size() > 1 ? "s" : ""));
            classes.forEach(classInfo -> createStubs(classPool, classInfo.getName(), test));
            classes.forEach(classInfo -> processClass(classPool, classInfo.getName(), test));
        } catch (Throwable exception) {
            getLog().error(exception);
            throw new MojoExecutionException("Cannot run protobuf plugin", exception);
        }finally {
            getLog().info("Processing took %sms".formatted(System.currentTimeMillis() - time));
        }
    }

    @SneakyThrows
    private void createStubs(ClassPool classPool, String className, boolean test) {
        var ctClass = classPool.get(className);
        if(isNonConcrete(ctClass)){
            return;
        }

        var methodName = getDeserializationMethod(ctClass);
        if(ctClass.getDeclaredMethods(SERIALIZATION_METHOD).length == 0) {
            ctClass.addMethod(CtMethod.make(SERIALIZATION_STUB.formatted(SERIALIZATION_METHOD), ctClass));
        }

        if(ctClass.getDeclaredMethods(methodName).length == 0) {
            ctClass.addMethod(CtMethod.make(DESERIALIZATION_STUB.formatted(toJavaType(ctClass), methodName), ctClass));
        }
    }

    private boolean isNonConcrete(CtClass ctClass) {
        return ctClass.isInterface() || Modifier.isAbstract(ctClass.getModifiers());
    }

    @SneakyThrows
    private void processClass(ClassPool classPool, String className, boolean test) {
        if(processed.contains(className)){
            return;
        }

        getLog().info("Processing %s".formatted(className));
        processed.add(className);
        var ctClass = classPool.get(className);
        if(isNonConcrete(ctClass)){
            getLog().info("Skipping non-concrete class");
            return;
        }

        var fields = getProtobufFields(ctClass);
        createSerializationMethod(classPool, ctClass, fields, test);
        createDeserializerMethod(classPool, ctClass, fields, test);
        ctClass.writeFile(test ? project.getBuild().getTestOutputDirectory() : project.getBuild().getOutputDirectory());
    }

    private void createSerializationMethod(ClassPool classPool, CtClass ctClass, Map<CtField, ProtobufProperty> fields, boolean test) throws NotFoundException, CannotCompileException  {
        var bodyBuilder = new StringWriter();
        try(var body = new PrintWriter(bodyBuilder)) {
            body.println("public byte[] %s() {".formatted(SERIALIZATION_METHOD));
            body.println("%s output = new %s();".formatted(ARRAY_OUTPUT_STREAM, ARRAY_OUTPUT_STREAM));
            fields.forEach((field, annotation) -> {
                if(annotation.ignore()){
                    return;
                }

                var converter = getConverter(classPool, field, annotation, false);
                createSerializationField(classPool, body, field, annotation, converter, test);
            });
            body.println("return output.toByteArray();");
            body.println("}");
            addSafeMethod(ctClass, SERIALIZATION_METHOD, bodyBuilder.toString());
            getLog().info("Created serialization method");
        }
    }

    private CtMethod getConverter(ClassPool classPool, CtField field, ProtobufProperty annotation, boolean staticMethod) {
        try {
            var ctType = getImplementationType(classPool, field, annotation);
            var ctPrimitive = classPool.get(annotation.type().primitiveType().getName());
            var ctClass = classPool.get(annotation.type().wrappedType().getName());
            if (ctType.subtypeOf(ctPrimitive) || ctType.subtypeOf(ctClass)) {
                return null;
            }

            return Arrays.stream(ctType.getMethods())
                    .filter(entry -> entry.hasAnnotation(ProtobufConverter.class) && staticMethod == Modifier.isStatic(entry.getMethodInfo().getAccessFlags()))
                    .findFirst()
                    .orElseThrow(() -> new ProtobufSerializationException("Missing converter in %s".formatted(ctType.getName())));
        }catch (NotFoundException exception){
            throw new RuntimeException("Cannot find converter", exception);
        }
    }

    @SneakyThrows
    private CtClass getImplementationType(ClassPool classPool, CtField field, ProtobufProperty annotation) {
        try {
            var result = classPool.get(annotation.implementation().getName());
            return getConvertedWrapperType(classPool, field, annotation, result);
        }catch (UndeclaredThrowableException exception){
            var result = classPool.get(exception.getCause().getMessage());
            return getConvertedWrapperType(classPool, field, annotation, result);
        }
    }

    private CtClass getConvertedWrapperType(ClassPool classPool, CtField field, ProtobufProperty annotation, CtClass implementation) {
      try {
          if (!Objects.equals(implementation.getName(), ProtobufMessage.class.getName())) {
              return implementation;
          }

          if (annotation.repeated()) {
              return classPool.get(annotation.type().wrappedType().getName());
          }

          return field.getType();
      }catch (NotFoundException exception){
          throw new RuntimeException("Cannot find implementation", exception);
      }
    }

    @SneakyThrows
    private void createSerializationField(ClassPool classPool, PrintWriter body, CtField field, ProtobufProperty annotation, CtMethod converter, boolean test) {
        if(!field.getType().isPrimitive()) {
            body.println("if(%s != null) {".formatted(field.getName()));
        }
        switch (annotation.type()){
            case MESSAGE -> createSerializationMessage(classPool, body, field, annotation, converter, test);
            case FLOAT -> createSerializationAny(classPool, body, annotation, converter, "Float", field);
            case DOUBLE -> createSerializationAny(classPool, body, annotation, converter, "Double", field);
            case BOOL -> createSerializationAny(classPool, body, annotation, converter, "Bool", field);
            case STRING -> createSerializationAny(classPool, body, annotation, converter, "String", field);
            case BYTES -> createSerializationAny(classPool, body, annotation, converter, "Bytes", field);
            case INT32, SINT32 -> createSerializationAny(classPool, body, annotation, converter, "Int32", field);
            case UINT32 -> createSerializationAny(classPool, body, annotation, converter, "UInt32", field);
            case FIXED32, SFIXED32 -> createSerializationAny(classPool, body, annotation, converter, "Fixed32", field);
            case INT64, SINT64 -> createSerializationAny(classPool, body, annotation, converter, "Int64", field);
            case UINT64 -> createSerializationAny(classPool, body, annotation, converter, "UInt64", field);
            case FIXED64, SFIXED64 -> createSerializationAny(classPool, body, annotation, converter, "Fixed64", field);
        }
        if (field.getType().isPrimitive()) {
            return;
        }

        body.println("}");
        if(!annotation.required()){
            return;
        }

        body.println("else");
        var fieldName = Objects.equals(annotation.name(), ProtobufProperty.DEFAULT_NAME) ? field.getName() : annotation.name();
        body.println("throw %s.missingMandatoryField(\"%s\");".formatted(PROTOBUF_SERIALIZATION_EXCEPTION, fieldName));
    }

    private void createSerializationMessage(ClassPool classPool, PrintWriter body, CtField field, ProtobufProperty annotation, CtMethod converter, boolean test) {
        var ctType = getImplementationType(classPool, field, annotation);
        processClass(classPool, ctType.getName(), test);
        if(ctType.isEnum()){
            if(annotation.repeated()) {
                createSerializationRepeatedFixed(classPool, field, annotation, converter, body);
                body.println("output.writeUInt32(%s, entry%s.index());".formatted(annotation.index(), toMethodCall(converter)));
                body.println("}");
                return;
            }

            var getter = findGetter(classPool, field, annotation);
            body.println("output.writeUInt32(%s, %s%s.index());".formatted(annotation.index(), getter, toMethodCall(converter)));
            return;
        }

        if(annotation.repeated()){
            createSerializationRepeatedFixed(classPool, field, annotation, converter, body);
            body.println("output.writeBytes(%s, entry%s.%s());".formatted(annotation.index(), toMethodCall(converter), SERIALIZATION_METHOD));
            body.println("}");
            return;
        }

        var getter = findGetter(classPool, field, annotation);
        body.println("output.writeBytes(%s, %s%s.%s());".formatted(annotation.index(), getter, toMethodCall(converter), SERIALIZATION_METHOD));
    }

    private void createSerializationAny(ClassPool classPool, PrintWriter body, ProtobufProperty annotation, CtMethod converter, String writeType, CtField field) {
        if(annotation.repeated()){
            createSerializationRepeatedFixed(classPool, field, annotation, converter, body);
            body.println("output.write%s(%s, entry%s);".formatted(writeType, annotation.index(), toMethodCall(converter)));
            body.println("}");
            return;
        }

        var getter = findGetter(classPool, field, annotation);
        body.println("output.write%s(%s, %s%s);".formatted(writeType, annotation.index(), getter, toMethodCall(converter)));
    }

    private void createSerializationRepeatedFixed(ClassPool classPool, CtField field, ProtobufProperty annotation, CtMethod converter, PrintWriter body) {
        var implementationType = getImplementationType(classPool, field, annotation).getName();
        var getter = findGetter(classPool, field, annotation);
        body.println("java.util.Iterator iterator = %s.iterator();".formatted(getter));
        body.println("%s entry;".formatted(implementationType));
        body.println("while(iterator.hasNext()) {");
        body.println("entry = (%s) iterator.next();".formatted(implementationType));
    }

    private String toMethodCall(CtMethod converter) {
        return converter != null ? ".%s()".formatted(converter.getName()) : "";
    }

    @SneakyThrows
    private String findGetter(ClassPool classPool, CtField ctField, ProtobufProperty annotation){
        if (!annotation.repeated()) {
            return ctField.getName();
        }

        var ctClass = ctField.getDeclaringClass();
        var collection = classPool.get(Collection.class.getName());
        if(ctField.getType().subtypeOf(collection)){
            return ctField.getName();
        }

        var accessor = findMethod(ctClass, ctField.getName())
                .or(() -> findMethod(ctClass, "get" + ctField.getName().substring(0, 1).toUpperCase() + ctField.getName().substring(1)))
                .orElseThrow(() -> new NoSuchElementException("Missing getter/accessor for repeated field %s in %s".formatted(ctField.getName(), ctClass.getName())));
        if(!accessor.getReturnType().subtypeOf(collection)){
            throw new IllegalStateException("Unexpected getter/accessor for repeated field %s in %s: expected return type to extend Collection".formatted(ctField.getName(), ctClass.getName()));
        }

        return accessor.getName() + "()";
    }

    private Optional<CtMethod> findMethod(CtClass ctClass, String name, CtClass... params){
        try {
            return Optional.ofNullable(ctClass.getDeclaredMethod(name, params));
        }catch (NotFoundException exception){
            return Optional.empty();
        }
    }

    private void createDeserializerMethod(ClassPool classPool, CtClass ctClass, Map<CtField, ProtobufProperty> fields, boolean test) throws NotFoundException, CannotCompileException {
        var methodName = getDeserializationMethod(ctClass);
        if(ctClass.isEnum()){
            var methodBody = DESERIALIZATION_ENUM_BODY.formatted(ctClass.getName(), DESERIALIZATION_ENUM_METHOD, ctClass.getName(), ctClass.getName());
            addSafeMethod(ctClass, methodName, methodBody);
            return;
        }

        var builderMethod = getBuilderMethod(ctClass);
        if(builderMethod.isEmpty()){
            getLog().error("Missing builder() in %s".formatted(ctClass.getName()));
            return;
        }

        var bodyBuilder = new StringWriter();
        try(var body = new PrintWriter(bodyBuilder)) {
            body.println("public static %s %s(byte[] bytes) {".formatted(toJavaType(ctClass), methodName));
            var requiredFields = fields.entrySet()
                    .stream()
                    .filter(entry -> !entry.getValue().ignore() && entry.getValue().required())
                    .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue));
            if(!requiredFields.isEmpty()){
                body.println("java.util.BitSet fields = new java.util.BitSet();");
            }
            body.println("%s builder = builder();".formatted(toJavaType(builderMethod.get().getReturnType())));
            body.println("%s input = new %s(bytes);".formatted(ARRAY_INPUT_STREAM, ARRAY_INPUT_STREAM));
            var repeatedFields = fields.entrySet()
                    .stream()
                    .filter(entry -> !entry.getValue().ignore() && entry.getValue().repeated())
                    .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue));
            repeatedFields.forEach((field, annotation) -> createRepeatedFields(body, field));
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

                var converter = getConverter(classPool, field, annotation, true);
                createDeserializerField(classPool, body, field, annotation, converter, test);
            });
            body.println("default:");
            body.println("input.readBytes();");
            body.println("break;");
            body.println("}");
            body.println("}");
            repeatedFields.forEach((field, annotation) -> body.println("builder.%s(%sValues);".formatted(field.getName(), field.getName())));
            requiredFields.forEach((field, property) -> {
                body.println("if(!fields.get(%s))".formatted(property.index()));
                var fieldName = Objects.equals(property.name(), ProtobufProperty.DEFAULT_NAME) ? field.getName() : property.name();
                body.println("throw %s.missingMandatoryField(\"%s\");".formatted(PROTOBUF_DESERIALIZATION_EXCEPTION, fieldName));
            });
            body.println("return builder.build();");
            body.println("}");
            addSafeMethod(ctClass, methodName, bodyBuilder.toString());
            getLog().info("Created deserialization method");
        }
    }

    private void createRepeatedFields(PrintWriter body, CtField field) {
        var implementationName = getCollectionType(field);
        body.println("%s %sValues = new %s();".formatted(implementationName, field.getName(), implementationName));
    }

    @SneakyThrows
    private String getCollectionType(CtField field) {
        var implementation = field.getType();
        if (!isNonConcrete(implementation)) {
            return implementation.getName();
        }

        if(implementation.getName().equals(List.class.getName()) || implementation.getName().equals(Map.class.getName())){
            return ArrayList.class.getName();
        }

        throw new IllegalArgumentException("Unexpected collection type for repeated field %s in %s: %s".formatted(field.getName(), field.getDeclaringClass().getName(), implementation.getName()));
    }

    private void addSafeMethod(CtClass ctClass, String methodName, String body) throws CannotCompileException, NotFoundException {
        var method = CtMethod.make(body, ctClass);
        var existingMethods = ctClass.getDeclaredMethods(methodName);
        for (var existingMethod : existingMethods) {
            ctClass.removeMethod(existingMethod);
        }
        ctClass.addMethod(method);
    }

    private String toJavaType(CtMethod ctMethod){
        return toJavaType(ctMethod.getDeclaringClass()) + "." + ctMethod.getName();
    }

    private String toJavaType(CtClass ctClass){
        return toJavaType(ctClass.getName());
    }

    private String toJavaType(String type){
        return type.replaceAll("\\$", ".");
    }

    private void createDeserializerField(ClassPool classPool, PrintWriter body, CtField field, ProtobufProperty annotation, CtMethod converter, boolean test) {
        body.println("case %s: ".formatted(annotation.index()));
        switch (annotation.type()){
            case MESSAGE -> createDeserializerMessage(classPool, field, annotation, converter, body, test);
            case BOOL -> createDeserializerBoolean(body, field, annotation, converter);
            case STRING -> createDeserializerString(body, field, annotation, converter);
            case BYTES -> createDeserializerBytes(body, field, annotation, converter);
            case FLOAT, DOUBLE, INT32, SINT32, UINT32, FIXED32, SFIXED32, FIXED64, SFIXED64, INT64, SINT64, UINT64 -> createDeserializerScalar(body, field, annotation, converter);
        }
    }

    private void createDeserializerScalar(PrintWriter body, CtField field, ProtobufProperty annotation, CtMethod converter) {
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

    @SneakyThrows
    private void writeDeserializerFixedScalar(PrintWriter body, CtField field, ProtobufProperty annotation, CtMethod converter, String methodName) {
        if(annotation.repeated()){
            if(converter != null){
                body.println("%sValues.add(%s.valueOf(%s(input.read%s())));".formatted(field.getName(), annotation.type().wrappedType().getName(), toJavaType(converter), methodName));
            }else {
                body.println("%sValues.add(%s.valueOf(input.read%s()));".formatted(field.getName(), annotation.type().wrappedType().getName(), methodName));
            }
        }else {
            var primitive = field.getType().isPrimitive();
            if(converter != null){
                body.println("builder.%s(%s(input.read%s()));".formatted(field.getName(), toJavaType(converter), methodName));
            }else {
                if(primitive) {
                    body.println("builder.%s(input.read%s());".formatted(field.getName(), methodName));
                }else {
                    body.println("builder.%s(%s.valueOf(input.read%s()));".formatted(field.getName(), annotation.type().wrappedType().getName(), methodName));
                }
            }
        }
        body.println("break;");
    }

    private void createDeserializerBytes(PrintWriter body, CtField field, ProtobufProperty annotation, CtMethod converter) {
        body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_LENGTH_DELIMITED , PROTOBUF_DESERIALIZATION_EXCEPTION));
        if(converter != null){
            var converterName = toJavaType(converter);
            if(annotation.repeated()){
                body.println("%sValues.add(%s(input.readBytes()));".formatted(field.getName(), converterName));
            }else {
                body.println("builder.%s(%s(input.readBytes()));".formatted(field.getName(), converterName));
            }
        }else {
            if(annotation.repeated()){
                body.println("%sValues.add(input.readBytes());".formatted(field.getName()));
            }else {
                body.println("builder.%s(input.readBytes());".formatted(field.getName()));
            }
        }
        body.println("break;");
    }

    private void createDeserializerString(PrintWriter body, CtField field, ProtobufProperty annotation, CtMethod converter) {
        body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_LENGTH_DELIMITED, PROTOBUF_DESERIALIZATION_EXCEPTION));
        if(converter != null){
            var converterName = toJavaType(converter);
            if(annotation.repeated()){
                body.println("%sValues.add(%s(new String(input.readBytes(), java.nio.charset.StandardCharsets.UTF_8)));".formatted(field.getName(), converterName));
            }else {
                body.println("builder.%s(%s(new String(input.readBytes(), java.nio.charset.StandardCharsets.UTF_8)));".formatted(field.getName(), converterName));
            }
        }else {
            if(annotation.repeated()){
                body.println("%sValues.add(new String(input.readBytes(), java.nio.charset.StandardCharsets.UTF_8));".formatted(field.getName()));
            }else {
                body.println("builder.%s(new String(input.readBytes(), java.nio.charset.StandardCharsets.UTF_8));".formatted(field.getName()));
            }
        }
        body.println("break;");
    }

    private void createDeserializerMessage(ClassPool classPool, CtField field, ProtobufProperty annotation, CtMethod converter, PrintWriter body, boolean test) {
        var implementationType = getImplementationType(classPool, field, annotation);
        var deserializationMethod = getDeserializationMethod(implementationType);
        processClass(classPool, implementationType.getName(), test);
        if(implementationType.isEnum()){
            body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_VAR_INT, PROTOBUF_DESERIALIZATION_EXCEPTION));
        }else {
            body.println("if(tag != %s && tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_LENGTH_DELIMITED, WIRE_TYPE_EMBEDDED_MESSAGE, PROTOBUF_DESERIALIZATION_EXCEPTION));
        }
        var readMethod = implementationType.isEnum() ? "Int32" : "Bytes";
        var implementation = toJavaType(implementationType);
        if(converter != null){
            var converterName = toJavaType(converter);
            if (annotation.repeated()) {
                body.println("%sValues.add(%s(%s.%s(input.read%s())));".formatted(field.getName(), converterName, implementation, deserializationMethod, readMethod));
            } else {
                body.println("builder.%s(%s(%s.%s(input.read%s())));".formatted(field.getName(), converterName, implementation, deserializationMethod, readMethod));
            }
        }else {
            if (annotation.repeated()) {
                body.println("%sValues.add(%s.%s(input.read%s()));".formatted(field.getName(), implementation, deserializationMethod, readMethod));
            } else {
                body.println("builder.%s(%s.%s(input.read%s()));".formatted(field.getName(), implementation, deserializationMethod, readMethod));
            }
        }
        body.println("break;");
    }

    private String getDeserializationMethod(CtClass implementationType) {
        return implementationType.isEnum() ? DESERIALIZATION_ENUM_METHOD : DESERIALIZATION_CLASS_METHOD;
    }

    private void createDeserializerBoolean(PrintWriter body, CtField field, ProtobufProperty annotation, CtMethod converter) {
        if(annotation.repeated()){
            body.println("switch(tag) {");
            body.println("case %s:".formatted(WIRE_TYPE_LENGTH_DELIMITED));
            if(converter != null){
                body.println("%sValues.addAll(%s(input.readBoolPacked()));".formatted(field.getName(), toJavaType(converter)));
            }else {
                body.println("%sValues.addAll(input.readBoolPacked());".formatted(field.getName()));
            }
            body.println("break;");
            body.println("case %s:".formatted(WIRE_TYPE_VAR_INT));
            writeDeserializerFixedBoolean(body, field, annotation, converter);
            body.println("default: throw %s.invalidTag(tag);".formatted(PROTOBUF_DESERIALIZATION_EXCEPTION));
            body.println("}");
            body.println("break;");
            return;
        }

        body.println("if(tag != %s) throw %s.invalidTag(tag);".formatted(WIRE_TYPE_VAR_INT, PROTOBUF_DESERIALIZATION_EXCEPTION));
        writeDeserializerFixedBoolean(body, field, annotation, converter);
    }

    @SneakyThrows
    private void writeDeserializerFixedBoolean(PrintWriter body, CtField field, ProtobufProperty annotation, CtMethod converter) {
        if(annotation.repeated()){
            if(converter != null){
                body.println("%sValues.add(%s(input.readBool()));".formatted(field.getName(), toJavaType(converter)));
            }else {
                body.println("%sValues.add(input.readBool());".formatted(field.getName()));
            }
        }else {
            var primitive = field.getType().isPrimitive();
            if(converter != null){
                if(primitive) {
                    body.println("builder.%s(%s(input.readBool()));".formatted(field.getName(), toJavaType(converter)));
                }else {
                    body.println("builder.%s(Boolean.valueOf(%s(input.readBool())));".formatted(field.getName(), toJavaType(converter)));
                }
            }else {
                if(primitive) {
                    body.println("builder.%s(input.readBool());".formatted(field.getName()));
                }else {
                    body.println("builder.%s(Boolean.valueOf(input.readBool()));".formatted(field.getName()));
                }
            }
        }
        body.println("break;");
    }

    private Map<CtField, ProtobufProperty> getProtobufFields(CtClass ctClass) {
        return Stream.of(ctClass.getFields(), ctClass.getDeclaredFields())
                .flatMap(Arrays::stream)
                .map(this::getProtobufPropertyEntry)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Optional<Entry<CtField, ProtobufProperty>> getProtobufPropertyEntry(CtField entry) {
        try {
            var annotation = (ProtobufProperty) entry.getAnnotation(ProtobufProperty.class);
            if(annotation == null){
                return Optional.empty();
            }

            return Optional.of(Map.entry(entry, annotation));
        }catch (ClassNotFoundException exception){
            throw new RuntimeException("Cannot parse field", exception);
        }
    }

    private Optional<CtMethod> getBuilderMethod(CtClass clazz) {
        try {
            return Optional.ofNullable(clazz.getDeclaredMethod("builder"));
        }catch (NotFoundException exception){
            return Optional.empty();
        }
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
}
