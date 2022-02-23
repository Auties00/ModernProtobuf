package it.auties.protobuf.decoder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.github.classgraph.ClassGraph;
import it.auties.protobuf.annotation.ProtobufConfigurator;

import java.lang.reflect.InvocationTargetException;
import java.util.NoSuchElementException;

public interface ProtobufDecoderConfigurator {
    ProtobufDecoderConfigurator DEFAULT_INSTANCE = new ProtobufDecoderConfigurator() {};

    default ObjectMapper createMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(new Jdk8Module());
    }

    static ProtobufDecoderConfigurator findConfigurator(){
        return new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()
                .getClassesWithAnnotation(ProtobufConfigurator.class)
                .loadClasses()
                .stream()
                .findFirst()
                .map(ProtobufDecoderConfigurator::initializeConfigurator)
                .orElse(DEFAULT_INSTANCE);
    }

    
    private static ProtobufDecoderConfigurator initializeConfigurator(Class<?> clazz) {
        try {
            return (ProtobufDecoderConfigurator) clazz.getConstructor().newInstance();
        }catch (NoSuchMethodException exception){
            throw new NoSuchElementException("Cannot initialize a protobuf configurator that doesn't provide a no args constructor");
        }catch (InstantiationException | IllegalAccessException | InvocationTargetException exception){
            throw new RuntimeException("An exception occurred while initializing a protobuf configurator", exception);
        }
    }
}
