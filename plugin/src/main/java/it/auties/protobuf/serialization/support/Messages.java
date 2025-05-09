package it.auties.protobuf.serialization.support;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public class Messages {
    private final ProcessingEnvironment processingEnv;
    public Messages(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void printWarning(String msg, Element element) {
        if(element == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, msg);
        }else {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, msg, element);
        }
    }

    public void printError(String msg, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
    }

    public void printInfo(String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg);
    }
}
