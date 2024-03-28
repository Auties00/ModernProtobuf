package it.auties.protobuf.serialization.generator.clazz;

import javax.annotation.processing.Filer;

public abstract class ProtobufClassVisitor {
    protected final Filer filer;
    public ProtobufClassVisitor(Filer filer) {
        this.filer = filer;
    }
}
