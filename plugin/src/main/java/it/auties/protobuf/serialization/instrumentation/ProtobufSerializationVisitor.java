package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.model.ProtobufProperty;
import it.auties.protobuf.stream.ProtobufOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import static it.auties.protobuf.Protobuf.SERIALIZATION_METHOD;

public class ProtobufSerializationVisitor extends ProtobufInstrumentationVisitor {
    public ProtobufSerializationVisitor(ProtobufMessageElement element, ClassWriter classWriter) {
        super(element, classWriter);
    }

    @Override
    protected void doInstrumentation() {
        var outputStreamId = createOutputStream();
        writeProperties(outputStreamId);
        createReturnSerializedValue(outputStreamId);
    }

    @Override
    public int access() {
        return Opcodes.ACC_PUBLIC;
    }
    
    @Override
    public String name() {
        return SERIALIZATION_METHOD;
    }
    
    @Override
    public String descriptor() {
        var versionType = Type.getType(ProtobufVersion.class);
        return "(L%s;)[B".formatted(versionType.getInternalName());
    }

    @Override
    protected String signature() {
        return null;
    }

    @Override
    public int argsCount() {
        return 1;
    }

    // Writes all properties to the output stream
    private void writeProperties(int outputStreamId) {
        element.properties()
                .forEach(property -> writeProperty(outputStreamId, property));
    }

    // Writes a property to the output stream
    private void writeProperty(int outputStreamId, ProtobufProperty property) {
        methodVisitor.visitVarInsn(
                Opcodes.ALOAD,
                outputStreamId
        );
        pushIntToStack(
                property.index()
        );
        methodVisitor.visitVarInsn(
                Opcodes.ALOAD,
                0 // this
        );
        var fieldType = Objects.requireNonNullElse(property.wrapperType(), property.javaType());
        methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                element.className(),
                property.name(),
                fieldType.getDescriptor()
        );
        var readMethod = getSerializerStreamMethod(property);
        var convertedType = createJavaPropertySerializer(
                property
        );
        boxValueIfNecessary(
                convertedType.orElse(fieldType),
                readMethod
        );
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Type.getType(ProtobufOutputStream.class).getInternalName(),
                readMethod.getName(),
                Type.getMethodDescriptor(readMethod),
                false
        );
    }

    private void boxValueIfNecessary(Type fieldType, Method readMethod) {
        if (fieldType.getSort() == Type.OBJECT || fieldType.getSort() == Type.ARRAY) {
            return;
        }

        var paramTypes = readMethod.getParameterTypes();
        var paramToSerializeType = Type.getType(paramTypes[paramTypes.length - 1]);
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                paramToSerializeType.getInternalName(),
                "valueOf",
                "(%s)%s".formatted(fieldType.getDescriptor(), paramToSerializeType.getDescriptor()),
                false
        );
    }

    private Optional<Type> createJavaPropertySerializer(ProtobufProperty property) {
        var methodName = getJavaPropertyConverterMethodName(property);
        if(methodName == null) {
            return Optional.empty();
        }

        var methodDescriptor = getJavaPropertyConverterDescriptor(property);
        pushJavaPropertyConverterArgsToStack(property);
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Objects.requireNonNullElse(property.wrapperType(), property.javaType()).getInternalName(),
                methodName,
                methodDescriptor.getInternalName(),
                false
        );
        return Optional.of(methodDescriptor.getReturnType());
    }

    private String getJavaPropertyConverterMethodName(ProtobufProperty property) {
        return switch (property.protoType()) {
            case MESSAGE -> SERIALIZATION_METHOD;
            case ENUM -> "index";
            case STRING -> "getBytes";
            default -> null;
        };
    }

    private Type getJavaPropertyConverterDescriptor(ProtobufProperty property) {
        try {
            return Type.getType(switch (property.protoType()) {
                case MESSAGE -> descriptor();
                case ENUM -> "()I";
                case STRING -> Type.getMethodDescriptor(String.class.getMethod("getBytes", Charset.class));
                default -> throw new IllegalStateException("Unexpected value: " + property.protoType());
            });
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot get java property converter descriptor", exception);
        }
    }

    private void pushJavaPropertyConverterArgsToStack(ProtobufProperty property) {
        if(property.protoType() != ProtobufType.STRING) {
            return;
        }

        methodVisitor.visitFieldInsn(
                Opcodes.GETSTATIC,
                Type.getType(StandardCharsets.class).getInternalName(),
                "UTF_8",
                Type.getType(String.class).getDescriptor()
        );
    }

    // Returns the serialized value
    private void createReturnSerializedValue(int outputStreamId) {
       try {
           methodVisitor.visitVarInsn(
                   Opcodes.ALOAD,
                   outputStreamId
           );
           methodVisitor.visitMethodInsn(
                   Opcodes.INVOKEVIRTUAL,
                   Type.getType(ProtobufOutputStream.class).getInternalName(),
                   "toByteArray",
                   Type.getMethodDescriptor(ProtobufOutputStream.class.getMethod("toByteArray")),
                   false
           );
           methodVisitor.visitInsn(
                   Opcodes.ARETURN
           );
       }catch (NoSuchMethodException exception) {
           throw new RuntimeException("Missing toByteArray method", exception);
       }
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private Method getSerializerStreamMethod(ProtobufProperty annotation) {
        var clazz = ProtobufOutputStream.class;
        try {
            return switch (annotation.protoType()) {
                case MESSAGE, BYTES, STRING -> {
                    if (annotation.packed()) {
                        throw new IllegalArgumentException("%s %s is packed: only scalar types are allowed to have this modifier".formatted(annotation.protoType().name(), annotation.name()));
                    }

                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeBytes", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeBytes", int.class, byte[].class);
                }
                case ENUM, INT32, SINT32, BOOL -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeInt32", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeInt32", int.class, Integer.class);
                }
                case UINT32 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeUInt32", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeUInt32", int.class, Integer.class);
                }
                case FLOAT -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeFloat", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeFloat", int.class, Float.class);
                }
                case DOUBLE -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeDouble", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeDouble", int.class, Double.class);
                }
                case FIXED32, SFIXED32 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeFixed32", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeFixed32", int.class, Integer.class);
                }
                case INT64, SINT64 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeInt64", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeInt64", int.class, Long.class);
                }
                case UINT64 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeUInt64", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeUInt64", int.class, Long.class);
                }
                case FIXED64, SFIXED64 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeFixed64", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeFixed64", int.class, Long.class);
                }
            };
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Missing serializer method", exception);
        }
    }

    // Creates a ProtobufOutputStream from the first parameter of the method(ProtobufVersion)
    private int createOutputStream() {
        try {
            var outputStreamType = Type.getType(ProtobufOutputStream.class);
            methodVisitor.visitTypeInsn(
                    Opcodes.NEW,
                    outputStreamType.getInternalName()
            );
            methodVisitor.visitInsn(
                    Opcodes.DUP
            );
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    1
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    outputStreamType.getInternalName(),
                    "<init>",
                    Type.getConstructorDescriptor(ProtobufOutputStream.class.getConstructor(ProtobufVersion.class)),
                    false
            );
            var localVariableId = createLocalVariable();
            methodVisitor.visitVarInsn(
                    Opcodes.ASTORE,
                    localVariableId
            );
            return localVariableId;
        }catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot create input stream var", throwable);
        }
    }
}
