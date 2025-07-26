package it.auties.protobuf.serialization.generator;

import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;

public abstract class ProtobufClassGenerator {
    protected final Filer filer;
    public ProtobufClassGenerator(Filer filer) {
        this.filer = filer;
    }

    protected String getGeneratedClassNameByName(TypeElement element, String className) {
        var name = new StringBuilder();
        while (element.getEnclosingElement() instanceof TypeElement parent) {
            name.append(parent.getSimpleName());
            element = parent;
        }
        return name + className;
    }
}
