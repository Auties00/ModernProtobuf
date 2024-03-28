package it.auties.protobuf.serialization.support;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public class Messages {
    private final ProcessingEnvironment processingEnv;
    public Messages(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void printWarning(String msg, Element constructor) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, constructor);
    }

    public void printError(String msg, Element constructor) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, constructor);
    }
}
