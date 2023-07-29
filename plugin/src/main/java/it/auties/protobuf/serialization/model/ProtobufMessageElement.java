package it.auties.protobuf.serialization.model;

import it.auties.protobuf.annotation.ProtobufProperty;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ProtobufMessageElement {
    private final Type classType;
    private final TypeElement typeElement;
    private final Path targetFile;
    private final Map<Integer, ProtobufPropertyStub> properties;
    private final Map<Integer, String> constants;
    private ClassReader classReader;

    public ProtobufMessageElement(String binaryName, TypeElement typeElement, Path targetFile) {
        this.classType = Type.getObjectType(binaryName);
        this.typeElement = typeElement;
        this.targetFile = targetFile;
        this.properties = new LinkedHashMap<>();
        this.constants = new LinkedHashMap<>();
    }

    public TypeElement element() {
        return typeElement;
    }

    public ClassReader classReader() {
        if(classReader == null) {
            createClassReader();
        }

        return classReader;
    }

    private void createClassReader() {
        try {
            var read = Files.readAllBytes(targetFile);
            this.classReader = new ClassReader(read);
        }catch (IOException throwable) {
            throw new UncheckedIOException("Cannot read .class", throwable);
        }
    }

    public Path targetFile() {
        return targetFile;
    }

    public Type classType() {
        return classType;
    }

    public List<ProtobufPropertyStub> properties() {
        return List.copyOf(properties.values());
    }

    public boolean isEnum() {
        return typeElement.getKind() == ElementKind.ENUM;
    }

    public Map<Integer, String> constants() {
        return Collections.unmodifiableMap(constants);
    }

    public Optional<String> addConstant(int fieldIndex, String fieldName) {
        return Optional.ofNullable(constants.put(fieldIndex, fieldName));
    }

    public Optional<ProtobufPropertyStub> addProperty(VariableElement element, ProtobufPropertyType type, ProtobufProperty property) {
        if(property.ignored()) {
            return Optional.empty();
        }

        var fieldName = element.getSimpleName().toString();
        var fieldIndex = property.index();
        var result = new ProtobufPropertyStub(
                fieldIndex,
                fieldName,
                property.type(),
                type.argumentType(),
                type.rawType(),
                type.isEnum(),
                property.required(),
                property.repeated(),
                property.packed()
        );
        return Optional.ofNullable(properties.put(fieldIndex, result));
    }
}
