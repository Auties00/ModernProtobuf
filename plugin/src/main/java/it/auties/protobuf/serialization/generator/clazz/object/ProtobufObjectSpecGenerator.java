package it.auties.protobuf.serialization.generator.clazz.object;

import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.generator.clazz.ProtobufClassGenerator;
import it.auties.protobuf.serialization.generator.method.deserialization.object.ProtobufObjectDeserializationGenerator;
import it.auties.protobuf.serialization.generator.method.deserialization.object.ProtobufObjectDeserializationOverloadGenerator;
import it.auties.protobuf.serialization.generator.method.serialization.object.ProtobufObjectSerializationGenerator;
import it.auties.protobuf.serialization.generator.method.serialization.object.ProtobufObjectSerializationOverloadGenerator;
import it.auties.protobuf.serialization.generator.method.serialization.object.ProtobufObjectSizeGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.object.ProtobufObjectType;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.writer.CompilationUnitWriter;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.annotation.processing.Filer;
import javax.lang.model.element.PackageElement;
import java.io.IOException;
import java.util.*;

public class ProtobufObjectSpecGenerator extends ProtobufClassGenerator {
    public ProtobufObjectSpecGenerator(Filer filer) {
        super(filer);
    }

    public void createClass(ProtobufObjectElement objectElement, PackageElement packageElement) throws IOException {
        // Names
        var simpleGeneratedClassName = getGeneratedClassNameBySuffix(objectElement.element(), "Spec");
        var qualifiedGeneratedClassName = packageElement != null ? packageElement + "." + simpleGeneratedClassName : simpleGeneratedClassName;
        var sourceFile = filer.createSourceFile(qualifiedGeneratedClassName);

        // Declare a new compilation unit
        try (var compilationUnitWriter = new CompilationUnitWriter(sourceFile.openWriter())) {
            // If a package is available, write it in the compilation unit
            if(packageElement != null) {
                compilationUnitWriter.printPackageDeclaration(packageElement.getQualifiedName().toString());
            }

            // Declare the imports needed for everything to work
            var imports = getSpecImports(objectElement);
            imports.forEach(compilationUnitWriter::printImportDeclaration);

            // Separate imports from classes
            compilationUnitWriter.printSeparator();

            // Declare the spec class
            try(var classWriter = compilationUnitWriter.printClassDeclaration(simpleGeneratedClassName)) {
                if(objectElement.type() == ProtobufObjectType.ENUM) {
                    var objectType = objectElement.element().getSimpleName().toString();
                    classWriter.println("private static final Map<Integer, %s> %s = new HashMap<>();".formatted(objectType, ProtobufObjectDeserializationGenerator.ENUM_VALUES_FIELD));
                    try(var staticInitBlock = classWriter.printStaticBlock()) {
                        for(var entry : objectElement.constants().entrySet()) {
                            staticInitBlock.println("%s.put(%s, %s.%s);".formatted(ProtobufObjectDeserializationGenerator.ENUM_VALUES_FIELD, entry.getKey(), objectType, entry.getValue()));
                        }
                    }
                }

                // Write the serializer
                var serializationOverloadVisitor = new ProtobufObjectSerializationOverloadGenerator(objectElement);
                serializationOverloadVisitor.generate(classWriter);
                var serializationVisitor = new ProtobufObjectSerializationGenerator(objectElement);
                serializationVisitor.generate(classWriter);

                // Write the deserializer
                var deserializationOverloadVisitor = new ProtobufObjectDeserializationOverloadGenerator(objectElement);
                deserializationOverloadVisitor.generate(classWriter);
                var deserializationVisitor = new ProtobufObjectDeserializationGenerator(objectElement);
                deserializationVisitor.generate(classWriter);

                // Write the size calculator
                var sizeVisitor = new ProtobufObjectSizeGenerator(objectElement);
                sizeVisitor.generate(classWriter);
            }
        }
    }

    // Get the imports to include in the compilation unit
    private List<String> getSpecImports(ProtobufObjectElement message) {
        if(message.type() == ProtobufObjectType.ENUM) {
            return List.of(
                    message.element().getQualifiedName().toString(),
                    Arrays.class.getName(),
                    Optional.class.getName(),
                    ProtobufOutputStream.class.getName(),
                    Map.class.getName(),
                    HashMap.class.getName()
            );
        }

        var imports = new ArrayList<String>();
        imports.add(message.element().getQualifiedName().toString());
        imports.add(ProtobufInputStream.class.getName());
        imports.add(ProtobufOutputStream.class.getName());
        imports.add(ProtobufWireType.class.getName());
        if (message.properties().stream().anyMatch(ProtobufPropertyElement::required)) {
            imports.add(Objects.class.getName());
        }

        return Collections.unmodifiableList(imports);
    }
}
