package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyStub;
import it.auties.protobuf.stream.ProtobufOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import static it.auties.protobuf.Protobuf.SERIALIZATION_CLASS_METHOD;
import static it.auties.protobuf.Protobuf.SERIALIZATION_ENUM_METHOD;

public class ProtobufSerializationVisitor extends ProtobufInstrumentationVisitor {
    public ProtobufSerializationVisitor(ProtobufMessageElement element, ClassWriter classWriter) {
        super(element, classWriter);
    }

    @Override
    protected void doInstrumentation() {
        if(element.isEnum()) {
            createEnumSerializer();
        }else {
            createMessageSerializer();
        }
    }

    @Override
    public boolean shouldInstrument() {
        return !element.isEnum() || !hasEnumSerializationMethod();
    }

    private boolean hasEnumSerializationMethod() {
        return element.element().getEnclosedElements()
                .stream()
                .anyMatch(this::isEnumSerializationMethod);
    }

    private boolean isEnumSerializationMethod(Element entry) {
        return entry instanceof ExecutableElement executableElement
                && executableElement.getSimpleName().contentEquals(name())
                && executableElement.getParameters().isEmpty();
    }


    private void createEnumSerializer() {
        methodVisitor.visitVarInsn(
                Opcodes.ALOAD,
                0 // this
        );
        var metadata = element.enumMetadata().orElseThrow();
        methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                element.classType().getInternalName(),
                metadata.field().getSimpleName().toString(),
                metadata.fieldType().getDescriptor()
        );
        methodVisitor.visitInsn(
                getReturnInstruction(metadata.fieldType())
        );
    }

    private void createMessageSerializer() {
        checkRequiredProperties();
        var outputStreamId = createOutputStream();
        element.properties().forEach(property -> writeProperty(outputStreamId, property));
        createReturnSerializedValue(outputStreamId);
    }

    @Override
    public int access() {
        return Opcodes.ACC_PUBLIC;
    }
    
    @Override
    public String name() {
        return element.isEnum() ? SERIALIZATION_ENUM_METHOD : SERIALIZATION_CLASS_METHOD;
    }
    
    @Override
    public String descriptor() {
        if(element.isEnum()) {
            return "()I";
        }

        var versionType = Type.getType(ProtobufVersion.class);
        return "(L%s;)[B".formatted(versionType.getInternalName());
    }

    @Override
    protected String signature() {
        return null;
    }

    // Writes a property to the output stream
    private void writeProperty(int outputStreamId, ProtobufPropertyStub property) {
        methodVisitor.visitVarInsn(
                Opcodes.ALOAD,
                outputStreamId
        );
        pushIntToStack(
                property.index()
        );
        var fieldType = loadPropertyIntoStack(property);
        var readMethod = getSerializerStreamMethod(property);
        var convertedType = createJavaPropertySerializer(
                property
        );
        boxValueIfNecessary(
                convertedType.orElse(fieldType)
        );
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Type.getType(ProtobufOutputStream.class).getInternalName(),
                readMethod.getName(),
                Type.getMethodDescriptor(readMethod),
                false
        );
    }

    private Type loadPropertyIntoStack(ProtobufPropertyStub property) {
        methodVisitor.visitVarInsn(
                Opcodes.ALOAD,
                0 // this
        );
        var fieldType = Objects.requireNonNullElse(property.wrapperType(), property.javaType());
        methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                element.classType().getInternalName(),
                property.name(),
                fieldType.getDescriptor()
        );
        return fieldType;
    }

    private Optional<Type> createJavaPropertySerializer(ProtobufPropertyStub property) {
        var methodName = getJavaPropertyConverterMethodName(property);
        if(methodName.isEmpty()) {
            return Optional.empty();
        }

        var methodDescriptor = getJavaPropertyConverterDescriptor(property);
        if(methodDescriptor.isEmpty()) {
            return Optional.empty();
        }

        methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Objects.requireNonNullElse(property.wrapperType(), property.javaType()).getInternalName(),
                methodName.get(),
                methodDescriptor.get().getInternalName(),
                false
        );
        return Optional.of(methodDescriptor.get().getReturnType());
    }

    // TODO: Support @ProtobufConverter
    private Optional<String> getJavaPropertyConverterMethodName(ProtobufPropertyStub property) {
        return Optional.empty();
    }

    // TODO: Support @ProtobufConverter
    private Optional<Type> getJavaPropertyConverterDescriptor(ProtobufPropertyStub property) {
        return Optional.empty();
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
    private Method getSerializerStreamMethod(ProtobufPropertyStub annotation) {
        try {
            var clazz = ProtobufOutputStream.class;
            if(annotation.isEnum()) {
                return annotation.repeated() ? clazz.getMethod("writeEnum", int.class, Collection.class)
                        : clazz.getMethod("writeEnum", int.class, ProtobufEnum.class);
            }

            return switch (annotation.protoType()) {
                case STRING ->
                        annotation.repeated() ? clazz.getMethod("writeString", int.class, Collection.class) : clazz.getMethod("writeString", int.class, String.class);
                case OBJECT ->
                        annotation.repeated() ? clazz.getMethod("writeMessage", int.class, Collection.class) : clazz.getMethod("writeMessage", int.class, ProtobufMessage.class);
                case BYTES ->
                        annotation.repeated() ? clazz.getMethod("writeBytes", int.class, Collection.class) : clazz.getMethod("writeBytes", int.class, byte[].class);
                case BOOL ->
                        annotation.repeated() ? clazz.getMethod("writeBool", int.class, Collection.class) : clazz.getMethod("writeBool", int.class, Boolean.class);
                case INT32, SINT32 ->
                        annotation.repeated() ? clazz.getMethod("writeInt32", int.class, Collection.class) : clazz.getMethod("writeInt32", int.class, Integer.class);
                case UINT32 ->
                        annotation.repeated() ? clazz.getMethod("writeUInt32", int.class, Collection.class) : clazz.getMethod("writeUInt32", int.class, Integer.class);
                case FLOAT ->
                        annotation.repeated() ? clazz.getMethod("writeFloat", int.class, Collection.class) : clazz.getMethod("writeFloat", int.class, Float.class);
                case DOUBLE ->
                        annotation.repeated() ? clazz.getMethod("writeDouble", int.class, Collection.class) : clazz.getMethod("writeDouble", int.class, Double.class);
                case FIXED32, SFIXED32 ->
                        annotation.repeated() ? clazz.getMethod("writeFixed32", int.class, Collection.class) : clazz.getMethod("writeFixed32", int.class, Integer.class);
                case INT64, SINT64 ->
                        annotation.repeated() ? clazz.getMethod("writeInt64", int.class, Collection.class) : clazz.getMethod("writeInt64", int.class, Long.class);
                case UINT64 ->
                        annotation.repeated() ? clazz.getMethod("writeUInt64", int.class, Collection.class) : clazz.getMethod("writeUInt64", int.class, Long.class);
                case FIXED64, SFIXED64 ->
                        annotation.repeated() ? clazz.getMethod("writeFixed64", int.class, Collection.class) : clazz.getMethod("writeFixed64", int.class, Long.class);
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
            var localVariableId = createLocalVariable(outputStreamType);
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
