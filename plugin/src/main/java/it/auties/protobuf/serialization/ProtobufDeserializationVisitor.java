package it.auties.protobuf.serialization;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.stream.ProtobufInputStream;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.lang.System.Logger;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static it.auties.protobuf.model.ProtobufWireType.*;
import static java.lang.System.Logger.Level.WARNING;

class ProtobufDeserializationVisitor extends ClassVisitor {
    private final ProtobufMessageElement element;
    private final Logger logger;

    protected ProtobufDeserializationVisitor(ProtobufMessageElement element, ClassVisitor classVisitor) {
        super(Opcodes.ASM9);
        this.element = element;
        this.cv = classVisitor;
        this.logger = System.getLogger("Protobuf");
    }

    @Override
    public void visitEnd() {
        var methodAccess = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
        var methodName = getMethodName();
        var methodDescriptor = getMethodDescriptor();
        var methodSignature = getMethodSignature();
        var methodVisitor = cv.visitMethod(
                methodAccess,
                methodName,
                methodDescriptor,
                methodSignature,
                new String[0]
        );
        methodVisitor.visitCode();
        if (element.isEnum()) {
            writeNamedEnumConstructor(methodVisitor);
        }else {
            writeClassConstructor(methodAccess, methodName, methodDescriptor, methodVisitor);
        }

        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
        cv.visitEnd();
    }

    private String getMethodName() {
        return element.isEnum() ? Protobuf.DESERIALIZATION_ENUM_METHOD
                : Protobuf.DESERIALIZATION_CLASS_METHOD;
    }

    private String getMethodDescriptor() {
        return element.isEnum() ? "(I)Ljava/util/Optional;" : "([B)L%s;".formatted(element.className());
    }

    private String getMethodSignature() {
        if (!element.isEnum()) {
            return null;
        }

        return "(I)Ljava/util/Optional<L%s;>;".formatted(element.className());
    }

    private void writeClassConstructor(int access, String methodName, String methodDescriptor, MethodVisitor methodVisitor) {
      try {
          var localCreator = new GeneratorAdapter(
                  methodVisitor,
                  access,
                  methodName,
                  methodDescriptor
          );
          var inputStreamLocalId = createInputStreamVar(localCreator);
          var properties = createDeserializedFields(localCreator);
          var whileOuterLabel = new Label();
          localCreator.visitLabel(whileOuterLabel);
          localCreator.visitVarInsn(
                  Opcodes.ALOAD,
                  inputStreamLocalId
          );
          localCreator.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  Type.getType(ProtobufInputStream.class).getInternalName(),
                  "readTag",
                  Type.getMethodDescriptor(ProtobufInputStream.class.getMethod("readTag")),
                  false
          );
          var rawTagId = localCreator.newLocal(
                  Type.INT_TYPE
          );
          localCreator.visitInsn(
                  Opcodes.DUP
          );
          localCreator.visitVarInsn(
                  Opcodes.ISTORE,
                  rawTagId
          );
          var whileInnerLabel = new Label();
          localCreator.visitJumpInsn(
                  Opcodes.IFNE,
                  whileInnerLabel
          );
          applyConstructorCall(localCreator, properties);
          applyNullChecks(localCreator, properties);
          localCreator.visitLabel(
                  whileInnerLabel
          );
          var indexId = createDeserializationIndexField(localCreator, rawTagId);
          var tagId = createDeserializationTagField(localCreator, rawTagId);
          var unknownPropertyLabel = new Label();
          localCreator.visitVarInsn(
                  Opcodes.ILOAD,
                  indexId
          );
          var deserializableProperties = element.properties()
                  .stream()
                  .filter(entry -> !entry.ignore())
                  .toList();
          var indexes = deserializableProperties.stream()
                  .mapToInt(ProtobufPropertyStub::index)
                  .toArray();
          var labels = IntStream.range(0, indexes.length)
                  .mapToObj(ignored -> new Label())
                  .toArray(Label[]::new);
          localCreator.visitLookupSwitchInsn(
                  unknownPropertyLabel,
                  indexes,
                  labels
          );
          localCreator.visitLabel(unknownPropertyLabel);
          localCreator.visitJumpInsn(
                  Opcodes.GOTO,
                  whileOuterLabel
          );
          for(var index = 0; index < deserializableProperties.size(); index++) {
              var property = deserializableProperties.get(index);
              var propertyLabel = labels[index];
              localCreator.visitLabel(propertyLabel);
              switch (property.protoType()) {
                  case MESSAGE -> createMessageDeserializer(property, localCreator, inputStreamLocalId, tagId, properties.get(property));
                  case ENUM -> createEnumDeserializer(property, localCreator, inputStreamLocalId, tagId, properties.get(property));
              }
              localCreator.visitJumpInsn(
                      Opcodes.GOTO,
                      whileOuterLabel
              );
          }
      }catch (NoSuchMethodException exception) {
          throw new RuntimeException("Cannot create message deserializer", exception);
      }
    }

    private void applyConstructorCall(LocalVariablesSorter localCreator, LinkedHashMap<ProtobufPropertyStub, Integer> properties) {
        localCreator.visitTypeInsn(
                Opcodes.NEW,
                element.className()
        );
        localCreator.visitInsn(
                Opcodes.DUP
        );
        var constructorArgsDescriptor = properties.entrySet()
                .stream()
                .peek(entry -> localCreator.visitVarInsn(Opcodes.ALOAD, entry.getValue()))
                .map(entry -> entry.getKey().javaType().getDescriptor())
                .collect(Collectors.joining(""));
        localCreator.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                element.className(),
                "<init>",
                "(%s)V".formatted(constructorArgsDescriptor),
                false
        );
        localCreator.visitInsn(
                Opcodes.ARETURN
        );
    }

    private void applyNullChecks(MethodVisitor methodVisitor, LinkedHashMap<ProtobufPropertyStub, Integer> properties) {
        properties.entrySet()
                .stream()
                .filter(entry -> entry.getKey().required())
                .forEach(entry -> applyNullCheck(methodVisitor, entry.getKey(), entry.getValue()));
    }

    private void applyNullCheck(MethodVisitor methodVisitor, ProtobufPropertyStub property, int localVariableId) {
        try {
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    localVariableId
            );
            methodVisitor.visitLdcInsn(
                    "Missing required field: " + property.name()
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getType(Objects.class).getInternalName(),
                    "requireNonNull",
                    Type.getMethodDescriptor(Objects.class.getMethod("requireNonNull", Object.class, String.class)),
                    false
            );
            methodVisitor.visitInsn(
                    Opcodes.POP
            );
        } catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot write message null check", throwable);
        }
    }

    private LinkedHashMap<ProtobufPropertyStub, Integer> createDeserializedFields(LocalVariablesSorter localCreator) {
        return element.properties()
                .stream()
                .map(entry -> Map.entry(entry, createDeserializedField(localCreator, entry)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
    }

    private int createDeserializedField(LocalVariablesSorter localCreator, ProtobufPropertyStub property) {
        addDeserializerDefaultValue(localCreator, property);
        var variableId = localCreator.newLocal(property.javaType());
        localCreator.visitVarInsn(
                Opcodes.ASTORE,
                variableId
        );
        return variableId;
    }

    private void addDeserializerDefaultValue(LocalVariablesSorter localCreator, ProtobufPropertyStub property) {
        if(property.wrapperType() != null){
            checkRepeatedNonAbstractWrapper(property);
            localCreator.visitTypeInsn(
                    Opcodes.NEW,
                    property.wrapperType().getInternalName()
            );
            localCreator.visitInsn(
                    Opcodes.DUP
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    property.wrapperType().getInternalName(),
                    "<init>",
                    "()V",
                    false
            );
            return;
        }

        var sort = property.javaType().getSort();
        switch (sort) {
            case Type.OBJECT, Type.ARRAY ->  localCreator.visitInsn(Opcodes.ACONST_NULL);
            case Type.INT, Type.BOOLEAN, Type.CHAR, Type.SHORT, Type.BYTE -> localCreator.visitInsn(Opcodes.ICONST_0);
            case Type.FLOAT -> localCreator.visitInsn(Opcodes.FCONST_0);
            case Type.DOUBLE -> localCreator.visitInsn(Opcodes.DCONST_0);
            case Type.LONG -> localCreator.visitInsn(Opcodes.LCONST_0);
            default -> throw new RuntimeException("Unexpected type: " + property.javaType().getClassName());
        }
    }

    private void checkRepeatedNonAbstractWrapper(ProtobufPropertyStub property) {
        var genericType = property.wrapperType().getClassName();
        try {
            var rawType = genericType.substring(0, genericType.indexOf("<"));
            var javaClass = Class.forName(rawType);
            if (!Modifier.isAbstract(javaClass.getModifiers())) {
                return;
            }

            throw new IllegalArgumentException("%s %s is abstract: this is not allowed for repeated types!".formatted(genericType, property.name()));
        }catch (ClassNotFoundException exception) {
            logger.log(WARNING, "Cannot check whether %s is a concrete type as it's not part of the std Java library".formatted(genericType));
        }
    }

    private int createInputStreamVar(LocalVariablesSorter localCreator) {
        try {
            var inputStreamType = Type.getType(ProtobufInputStream.class);
            localCreator.visitTypeInsn(
                    Opcodes.NEW,
                    inputStreamType.getInternalName()
            );
            localCreator.visitInsn(
                    Opcodes.DUP
            );
            localCreator.visitVarInsn(
                    Opcodes.ALOAD,
                    0
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    inputStreamType.getInternalName(),
                    "<init>",
                    Type.getConstructorDescriptor(ProtobufInputStream.class.getConstructor(byte[].class)),
                    false
            );
            var streamLocalId = localCreator.newLocal(inputStreamType);
            localCreator.visitVarInsn(
                    Opcodes.ASTORE,
                    streamLocalId
            );
            return streamLocalId;
        }catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot create input stream var", throwable);
        }
    }

    private void createEnumDeserializer(ProtobufPropertyStub property, LocalVariablesSorter localCreator, int inputStreamId, int tagId, int fieldId) {
        try {
            var correctTagLabel = new Label();
            localCreator.visitVarInsn(
                    Opcodes.ILOAD,
                    tagId
            );
            localCreator.visitIntInsn(
                    Opcodes.BIPUSH,
                    WIRE_TYPE_VAR_INT
            );
            localCreator.visitJumpInsn(
                    Opcodes.IF_ICMPEQ,
                    correctTagLabel
            );
            localCreator.visitVarInsn(
                    Opcodes.ILOAD,
                    tagId
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getType(ProtobufDeserializationException.class).getInternalName(),
                    "invalidTag",
                    Type.getMethodDescriptor(ProtobufDeserializationException.class.getMethod("invalidTag", int.class)),
                    false
            );
            localCreator.visitInsn(
                    Opcodes.ATHROW
            );
            localCreator.visitLabel(
                    correctTagLabel
            );
            localCreator.visitVarInsn(
                    Opcodes.ALOAD,
                    inputStreamId
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(ProtobufInputStream.class).getInternalName(),
                    "readInt32",
                    Type.getMethodDescriptor(ProtobufInputStream.class.getMethod("readInt32")),
                    false
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    property.javaType().getInternalName(),
                    Protobuf.DESERIALIZATION_ENUM_METHOD,
                    "(I)Ljava/lang/Optional;",
                    false
            );
            localCreator.visitInsn(
                    Opcodes.ACONST_NULL
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(Optional.class).getInternalName(),
                    "orElse",
                    "(Ljava/lang/Object;)L%s;".formatted(element.className()),
                    false
            );
            if (property.repeated()) {
                localCreator.visitVarInsn(
                        Opcodes.ALOAD,
                        fieldId
                );
                localCreator.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        property.wrapperType().getInternalName(),
                        "add",
                        Type.getMethodDescriptor(Collection.class.getMethod("add", Object.class)),
                        false
                );
            } else {
                localCreator.visitVarInsn(
                        Opcodes.ASTORE,
                        fieldId
                );
            }
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create message deserializer", exception);
        }
    }

    private void createMessageDeserializer(ProtobufPropertyStub property, LocalVariablesSorter localCreator, int inputStreamId, int tagId, int fieldId) {
      try {
          var correctTagLabel = new Label();
          localCreator.visitVarInsn(
                  Opcodes.ILOAD,
                  tagId
          );
          localCreator.visitIntInsn(
                  Opcodes.BIPUSH,
                  WIRE_TYPE_LENGTH_DELIMITED
          );
          localCreator.visitJumpInsn(
                  Opcodes.IF_ICMPEQ,
                  correctTagLabel
          );
          localCreator.visitVarInsn(
                  Opcodes.ILOAD,
                  tagId
          );
          localCreator.visitIntInsn(
                  Opcodes.BIPUSH,
                  WIRE_TYPE_EMBEDDED_MESSAGE
          );
          localCreator.visitJumpInsn(
                  Opcodes.IF_ICMPEQ,
                  correctTagLabel
          );
          localCreator.visitVarInsn(
                  Opcodes.ILOAD,
                  tagId
          );
          localCreator.visitMethodInsn(
                  Opcodes.INVOKESTATIC,
                  Type.getType(ProtobufDeserializationException.class).getInternalName(),
                  "invalidTag",
                  Type.getMethodDescriptor(ProtobufDeserializationException.class.getMethod("invalidTag", int.class)),
                  false
          );
          localCreator.visitInsn(
                  Opcodes.ATHROW
          );
          localCreator.visitLabel(
                  correctTagLabel
          );
          localCreator.visitVarInsn(
                  Opcodes.ALOAD,
                  inputStreamId
          );
          localCreator.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  Type.getType(ProtobufInputStream.class).getInternalName(),
                  "readBytes",
                  Type.getMethodDescriptor(ProtobufInputStream.class.getMethod("readBytes")),
                  false
          );
          localCreator.visitMethodInsn(
                  Opcodes.INVOKESTATIC,
                  property.javaType().getInternalName(),
                  Protobuf.DESERIALIZATION_CLASS_METHOD,
                  "([B)L" + property.javaType().getInternalName() + ";",
                  false
          );
          if (property.repeated()) {
              localCreator.visitVarInsn(
                      Opcodes.ALOAD,
                      fieldId
              );
              localCreator.visitMethodInsn(
                      Opcodes.INVOKEVIRTUAL,
                      property.wrapperType().getInternalName(),
                      "add",
                      Type.getMethodDescriptor(Collection.class.getMethod("add", Object.class)),
                      false
              );
          } else {
              localCreator.visitVarInsn(
                      Opcodes.ASTORE,
                      fieldId
              );
          }
      }catch (NoSuchMethodException exception) {
          throw new RuntimeException("Cannot create message deserializer", exception);
      }
    }

    private int createDeserializationTagField(LocalVariablesSorter localCreator, int rawTagId) {
        localCreator.visitVarInsn(
                Opcodes.ILOAD,
                rawTagId
        );
        localCreator.visitIntInsn(
                Opcodes.BIPUSH,
                7
        );
        localCreator.visitInsn(
                Opcodes.IAND
        );
        var tagId = localCreator.newLocal(
                Type.INT_TYPE
        );
        localCreator.visitInsn(
                Opcodes.DUP
        );
        localCreator.visitVarInsn(
                Opcodes.ISTORE,
                tagId
        );
        return tagId;
    }

    private int createDeserializationIndexField(LocalVariablesSorter localCreator, int rawTagId) {
        localCreator.visitVarInsn(
                Opcodes.ILOAD,
                rawTagId
        );
        localCreator.visitInsn(
                Opcodes.ICONST_3
        );
        localCreator.visitInsn(
                Opcodes.IUSHR
        );
        var indexId = localCreator.newLocal(
                Type.INT_TYPE
        );
        localCreator.visitInsn(
                Opcodes.DUP
        );
        localCreator.visitVarInsn(
                Opcodes.ISTORE,
                indexId
        );
        return indexId;
    }

    private void writeNamedEnumConstructor(MethodVisitor methodVisitor) {
        try {
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    element.className(),
                    "values",
                    "()[L" + element.className() + ";",
                    false
            );

            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getType(Arrays.class).getInternalName(),
                    "stream",
                    Type.getMethodDescriptor(Arrays.class.getMethod("stream", Object[].class)),
                    false
            );

            var lambdaParameters = createEnumConstructorLambda();
            methodVisitor.visitVarInsn(
                    Opcodes.ILOAD,
                    0
            );
            methodVisitor.visitInvokeDynamicInsn(
                    "test",
                    "(I)Z",
                    new Handle(
                            Opcodes.H_INVOKESTATIC,
                            Type.getType(LambdaMetafactory.class).getInternalName(),
                            "metafactory",
                            Type.getMethodDescriptor(LambdaMetafactory.class.getMethod("metafactory", MethodHandles.Lookup.class, String.class, MethodType.class, MethodType.class, MethodHandle.class, MethodType.class)),
                            false
                    ),
                    lambdaParameters
            );

            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    Type.getType(Stream.class).getInternalName(),
                    "filter",
                    Type.getMethodDescriptor(Stream.class.getMethod("filter", Predicate.class)),
                    true
            );

            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    Type.getType(Stream.class).getInternalName(),
                    "findFirst",
                    Type.getMethodDescriptor(Stream.class.getMethod("findFirst")),
                    true
            );

            methodVisitor.visitInsn(
                    Opcodes.ARETURN
            );
        } catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot instrument enum", throwable);
        }
    }

    private Object[] createEnumConstructorLambda() {
        var methodName = "lambda$of$0";
        var methodDescriptor = "(IL%s;)Z".formatted(element.className());
        var predicateLambdaMethod = cv.visitMethod(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
                methodName,
                methodDescriptor,
                null,
                new String[0]
        );
        predicateLambdaMethod.visitCode();
        predicateLambdaMethod.visitVarInsn(
                Opcodes.ALOAD,
                1
        );
        predicateLambdaMethod.visitFieldInsn(
                Opcodes.GETFIELD,
                element.className(),
                "index",
                "I"
        );
        predicateLambdaMethod.visitVarInsn(
                Opcodes.ILOAD,
                0
        );

        var notEqualsLabel = new Label();
        predicateLambdaMethod.visitJumpInsn(
                Opcodes.IF_ICMPNE,
                notEqualsLabel
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.ICONST_1
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.IRETURN
        );
        predicateLambdaMethod.visitLabel(
                notEqualsLabel
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.ICONST_0
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.IRETURN
        );
        predicateLambdaMethod.visitMaxs(-1, -1);
        predicateLambdaMethod.visitEnd();
        return new Object[]{
                Type.getType("(Ljava/lang/Object;)Z"),
                new Handle(
                        Opcodes.H_INVOKESTATIC,
                        element.className(),
                        methodName,
                        methodDescriptor,
                        false
                ),
                Type.getType("(I)Z")
        };
    }
}
