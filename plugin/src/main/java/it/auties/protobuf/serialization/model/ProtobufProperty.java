package it.auties.protobuf.serialization.model;

import it.auties.protobuf.model.ProtobufType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import java.util.concurrent.atomic.AtomicReference;

public record ProtobufProperty(int index, String name, ProtobufType protoType, Type javaType, AtomicReference<ClassReader> localJavaType, Type wrapperType,
                               boolean required, boolean repeated, boolean packed) {
}
