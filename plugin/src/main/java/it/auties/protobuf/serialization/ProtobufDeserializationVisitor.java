package it.auties.protobuf.serialization;

import it.auties.protobuf.base.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

class ProtobufDeserializationVisitor extends ClassVisitor {
    private final ProtobufMessageElement element;
    protected ProtobufDeserializationVisitor(ProtobufMessageElement element, ClassVisitor classVisitor) {
        super(Opcodes.ASM9);
        this.element = element;
        this.cv = classVisitor;
    }

    @Override
    public void visitEnd() {
        var methodDescriptor = getMethodDescriptor();
        var methodSignature = getMethodSignature();
        var methodVisitor = cv.visitMethod(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                element.isEnum() ? ProtobufMessage.DESERIALIZATION_ENUM_METHOD : ProtobufMessage.DESERIALIZATION_CLASS_METHOD,
                methodDescriptor,
                methodSignature,
                new String[0]
        );
        methodVisitor.visitCode();
        if(element.isEnum()) {
            writeNamedEnumConstructor(methodVisitor);
            return;
        }

        writeClassConstructor(methodDescriptor, methodVisitor);
    }

    private String getMethodDescriptor() {
        return element.isEnum() ? "(I)Ljava/util/Optional;" : "([B)L%s;".formatted(element.className());
    }

    private String getMethodSignature() {
        if (!element.isEnum()) {
            return null;
        }

        var className = element.className().substring(element.className().lastIndexOf("/") + 1);
        var simpleClassName = className.substring(className.lastIndexOf("$") + 1);
        return "(I)Ljava/util/Optional<L%s;>;".formatted(simpleClassName);
    }

    private void writeClassConstructor(String methodDescriptor, MethodVisitor methodVisitor) {
        try {
            var localCreator = new LocalVariablesSorter(api, methodDescriptor, methodVisitor);
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
                    "([B)V",
                    false
            );
            var streamLocalId = localCreator.newLocal(inputStreamType);
            localCreator.visitVarInsn(Opcodes.ASTORE, streamLocalId);
            element.fields().forEach((name, property) -> {
                localCreator.visitInsn(Opcodes.ACONST_NULL);
                var variableType = getLocalVariableType(property);
                var variableId = localCreator.newLocal(variableType);
                localCreator.visitVarInsn(Opcodes.ASTORE, variableId);
            });
            cv.visitEnd();
        }catch (Throwable throwable) {
            throw new RuntimeException("Cannot instrument class", throwable);
        }
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

          var intEqualityHandle = createIntEqualityLambda();
          methodVisitor.visitVarInsn(
                  Opcodes.ILOAD,
                  0
          );
          methodVisitor.visitInvokeDynamicInsn(
                  "test",
                  "(I)Ljava/util/function/Predicate;",
                  intEqualityHandle
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

          methodVisitor.visitEnd();

          cv.visitEnd();
      }catch (Throwable throwable) {
          throw new RuntimeException("Cannot instrument enum", throwable);
      }
    }

    private Handle createIntEqualityLambda() {
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

        var falseLabel = new Label();
        predicateLambdaMethod.visitJumpInsn(
                Opcodes.IF_ICMPNE,
                falseLabel
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.ICONST_1
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.IRETURN
        );
        predicateLambdaMethod.visitLabel(
                falseLabel
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.ICONST_0
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.IRETURN
        );
        predicateLambdaMethod.visitEnd();
        return new Handle(
                Opcodes.H_INVOKESTATIC,
                element.className(),
                methodName,
                methodDescriptor,
                false
        );
    }

    private Type getLocalVariableType(ProtobufPropertyStub property) {
        return property.type() != ProtobufType.MESSAGE ? Type.getType(property.type().wrappedType())
                : property.implementation();
    }
}
