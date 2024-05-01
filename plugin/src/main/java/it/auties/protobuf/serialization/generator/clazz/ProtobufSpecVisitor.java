package it.auties.protobuf.serialization.generator.clazz;

import it.auties.protobuf.serialization.generator.method.ProtobufDeserializationMethodGenerator;
import it.auties.protobuf.serialization.generator.method.ProtobufSerializationMethodGenerator;
import it.auties.protobuf.serialization.object.ProtobufMessageElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyElement;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.PackageElement;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class ProtobufSpecVisitor {
    private final ProcessingEnvironment processingEnv;

    public ProtobufSpecVisitor(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void createClass(ProtobufMessageElement result, PackageElement packageName) throws IOException {
        var simpleGeneratedClassName = result.getGeneratedClassNameBySuffix("Spec");
        var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;
        var sourceFile = processingEnv.getFiler().createSourceFile(qualifiedGeneratedClassName);
        try (var writer = new PrintWriter(sourceFile.openWriter())) {
            if(packageName != null) {
                writer.println("package %s;\n".formatted(packageName.getQualifiedName()));
            }

            var imports = getSpecImports(result);
            imports.forEach(entry -> writer.println("import %s;".formatted(entry)));
            if(!imports.isEmpty()){
                writer.println();
            }

            writer.println("public class %s {".formatted(simpleGeneratedClassName));
            var serializationVisitor = new ProtobufSerializationMethodGenerator(result, writer);
            serializationVisitor.instrument();
            var deserializationVisitor = new ProtobufDeserializationMethodGenerator(result, writer);
            deserializationVisitor.instrument();
            writer.println("}");
        }
    }

    protected List<String> getSpecImports(ProtobufMessageElement message) {
        if(message.isEnum()) {
            return List.of(
                    message.element().getQualifiedName().toString(),
                    Arrays.class.getName(),
                    Optional.class.getName()
            );
        }

        var imports = new ArrayList<String>();
        imports.add(message.element().getQualifiedName().toString());
        imports.add(ProtobufInputStream.class.getName());
        imports.add(ProtobufOutputStream.class.getName());
        if (message.properties().stream().anyMatch(ProtobufPropertyElement::required)) {
            imports.add(Objects.class.getName());
        }

        return Collections.unmodifiableList(imports);
    }
}
