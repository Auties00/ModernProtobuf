package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyConverter;
import it.auties.protobuf.serialization.model.ProtobufPropertyStub;
import it.auties.protobuf.stream.ProtobufOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
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
        if (property.repeated() && property.type().converter().isPresent()) {
            writeRepeatedConvertedPropertySerializer(outputStreamId, property);
        }else {
            writeAnyPropertySerializer(outputStreamId, property);
        }
    }

    private void writeAnyPropertySerializer(int outputStreamId, ProtobufPropertyStub property) {
        var nullLabel = createFieldNullCheck(property);
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
        nullLabel.ifPresent(methodVisitor::visitLabel);
    }

    private Optional<Label> createFieldNullCheck(ProtobufPropertyStub property) {
        if (property.type().converter().isEmpty()) {
            return Optional.empty();
        }

        var nullFieldLabel = new Label();
        loadPropertyIntoStack(property);
        methodVisitor.visitJumpInsn(
                Opcodes.IFNULL,
                nullFieldLabel
        );
        return Optional.of(nullFieldLabel);
    }

    private void writeRepeatedConvertedPropertySerializer(int outputStreamId, ProtobufPropertyStub property) {
       try {
           var nullFieldLabel = createFieldNullCheck(property)
                   .orElseThrow(() -> new IllegalStateException("Missing label for converted property"));
           var iteratorId = createLocalVariable(Type.getType(Iterator.class));
           var fieldType = loadPropertyIntoStack(property);
           methodVisitor.visitMethodInsn(
                   Opcodes.INVOKEINTERFACE,
                   Type.getType(Collection.class).getInternalName(),
                   "iterator",
                   Type.getMethodDescriptor(Collection.class.getMethod("iterator")),
                   true
           );
           methodVisitor.visitVarInsn(
                   Opcodes.ASTORE,
                   iteratorId
           );
           var loopStart = new Label();
           methodVisitor.visitLabel(
                   loopStart
           );
           methodVisitor.visitVarInsn(
                   Opcodes.ALOAD,
                   iteratorId
           );
           methodVisitor.visitMethodInsn(
                   Opcodes.INVOKEINTERFACE,
                   Type.getType(Iterator.class).getInternalName(),
                   "hasNext",
                   Type.getMethodDescriptor(Iterator.class.getMethod("hasNext")),
                   true
           );
           methodVisitor.visitJumpInsn(
                   Opcodes.IFEQ,
                   nullFieldLabel
           );
           var nextId = createLocalVariable(property.type().implementationType());
           methodVisitor.visitVarInsn(
                   Opcodes.ALOAD,
                   iteratorId
           );
           methodVisitor.visitMethodInsn(
                   Opcodes.INVOKEINTERFACE,
                   Type.getType(Iterator.class).getInternalName(),
                   "next",
                   Type.getMethodDescriptor(Iterator.class.getMethod("next")),
                   true
           );
           methodVisitor.visitTypeInsn(
                   Opcodes.CHECKCAST,
                   property.type().implementationType().getInternalName()
           );
           methodVisitor.visitVarInsn(
                   getStoreInstruction(property.type().implementationType()),
                   nextId
           );
           var nullNextLabel = new Label();
           methodVisitor.visitVarInsn(
                   getLoadInstruction(property.type().implementationType()),
                   nextId
           );
           methodVisitor.visitJumpInsn(
                   Opcodes.IFNULL,
                   nullNextLabel
           );
           methodVisitor.visitVarInsn(
                   Opcodes.ALOAD,
                   outputStreamId
           );
           pushIntToStack(
                   property.index()
           );
           methodVisitor.visitVarInsn(
                   getLoadInstruction(property.type().implementationType()),
                   nextId
           );
           var readMethod = getSerializerStreamMethod(
                   property
           );
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
           methodVisitor.visitLabel(nullNextLabel);
           methodVisitor.visitJumpInsn(
                   Opcodes.GOTO,
                   loopStart
           );
           methodVisitor.visitLabel(nullFieldLabel);
       }catch (NoSuchMethodException exception) {
           throw new RuntimeException("Missing method", exception);
       }
    }

    private Type loadPropertyIntoStack(ProtobufPropertyStub property) {
        methodVisitor.visitVarInsn(
                Opcodes.ALOAD,
                0 // this
        );
        methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                element.classType().getInternalName(),
                property.name(),
                property.type().fieldType().getDescriptor()
        );
        return property.type().fieldType();
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
                property.type().implementationType().getInternalName(),
                methodName.get(),
                methodDescriptor.get(),
                false
        );
        var asmMethodType = Type.getMethodType(methodDescriptor.get());
        return Optional.of(asmMethodType.getReturnType());
    }

    private Optional<String> getJavaPropertyConverterMethodName(ProtobufPropertyStub property) {
        return property.type()
                .converter()
                .map(ProtobufPropertyConverter::serializerName);
    }

    private Optional<String> getJavaPropertyConverterDescriptor(ProtobufPropertyStub property) {
        return property.type()
                .converter()
                .map(ProtobufPropertyConverter::serializerDescriptor);
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
            if(annotation.type().isEnum()) {
                return isConcreteRepeated(annotation) ? clazz.getMethod("writeEnum", int.class, Collection.class)
                        : clazz.getMethod("writeEnum", int.class, ProtobufEnum.class);
            }

            return switch (annotation.protoType()) {
                case STRING ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeString", int.class, Collection.class) : clazz.getMethod("writeString", int.class, String.class);
                case OBJECT ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeMessage", int.class, Collection.class) : clazz.getMethod("writeMessage", int.class, ProtobufMessage.class);
                case BYTES ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeBytes", int.class, Collection.class) : clazz.getMethod("writeBytes", int.class, byte[].class);
                case BOOL ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeBool", int.class, Collection.class) : clazz.getMethod("writeBool", int.class, Boolean.class);
                case INT32, SINT32 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeInt32", int.class, Collection.class) : clazz.getMethod("writeInt32", int.class, Integer.class);
                case UINT32 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeUInt32", int.class, Collection.class) : clazz.getMethod("writeUInt32", int.class, Integer.class);
                case FLOAT ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeFloat", int.class, Collection.class) : clazz.getMethod("writeFloat", int.class, Float.class);
                case DOUBLE ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeDouble", int.class, Collection.class) : clazz.getMethod("writeDouble", int.class, Double.class);
                case FIXED32, SFIXED32 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeFixed32", int.class, Collection.class) : clazz.getMethod("writeFixed32", int.class, Integer.class);
                case INT64, SINT64 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeInt64", int.class, Collection.class) : clazz.getMethod("writeInt64", int.class, Long.class);
                case UINT64 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeUInt64", int.class, Collection.class) : clazz.getMethod("writeUInt64", int.class, Long.class);
                case FIXED64, SFIXED64 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeFixed64", int.class, Collection.class) : clazz.getMethod("writeFixed64", int.class, Long.class);
            };
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Missing serializer method", exception);
        }
    }

    private boolean isConcreteRepeated(ProtobufPropertyStub annotation) {
        return annotation.repeated() && annotation.type().converter().isEmpty();
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
