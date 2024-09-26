package it.auties.protobuf.serialization.generator.clazz.group;

import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.generator.clazz.ProtobufClassGenerator;
import it.auties.protobuf.serialization.generator.method.deserialization.group.ProtobufRawGroupDeserializationGenerator;
import it.auties.protobuf.serialization.generator.method.serialization.group.ProtobufRawGroupSerializationGenerator;
import it.auties.protobuf.serialization.generator.method.serialization.group.ProtobufRawGroupSizeGenerator;
import it.auties.protobuf.serialization.model.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.support.JavaWriter;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.*;

public class ProtobufRawGroupSpecGenerator extends ProtobufClassGenerator {
    public ProtobufRawGroupSpecGenerator(Filer filer) {
        super(filer);
    }

    public void createClass(TypeElement rawGroup, ProtobufSerializerElement serializerElement, PackageElement packageName) throws IOException {
        // Names
        var simpleGeneratedClassName = getGeneratedClassNameBySuffix(rawGroup, "Spec");
        var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;


        var sourceFile = createSourceFile(qualifiedGeneratedClassName);
        if(sourceFile.isEmpty()) {
            return;
        }

        // Declare a new compilation unit
        try (var compilationUnitWriter = new JavaWriter.CompilationUnit(sourceFile.get().openWriter())) {
            // If a package is available, write it in the compilation unit
            if(packageName != null) {
                compilationUnitWriter.printPackageDeclaration(packageName.getQualifiedName().toString());
            }

            // Declare the imports needed for everything to work
            var imports = getSpecImports(rawGroup);
            imports.forEach(compilationUnitWriter::printImportDeclaration);

            // Separate imports from classes
            compilationUnitWriter.printSeparator();

            // Declare the spec class
            try(var classWriter = compilationUnitWriter.printClassDeclaration(simpleGeneratedClassName)) {
                // Write the serializer
                var serializationVisitor = new ProtobufRawGroupSerializationGenerator(rawGroup, serializerElement);
                serializationVisitor.generate(classWriter);

                // Write the deserializer
                var deserializationVisitor = new ProtobufRawGroupDeserializationGenerator(rawGroup, serializerElement);
                deserializationVisitor.generate(classWriter);

                // Write the size calculator
                var sizeVisitor = new ProtobufRawGroupSizeGenerator(rawGroup, serializerElement);
                sizeVisitor.generate(classWriter);
            }
        }
    }

    private Optional<JavaFileObject> createSourceFile(String qualifiedGeneratedClassName) throws IOException {
        try {
            return Optional.of(filer.createSourceFile(qualifiedGeneratedClassName));
        }catch (FilerException filerException) {
            return Optional.empty();
        }
    }

    // Get the imports to include in the compilation unit
    private List<String> getSpecImports(TypeElement rawGroup) {
        var imports = new ArrayList<String>();
        imports.add(rawGroup.getQualifiedName().toString());
        imports.add(ProtobufInputStream.class.getName());
        imports.add(ProtobufOutputStream.class.getName());
        imports.add(ProtobufWireType.class.getName());
        imports.add(Map.class.getName());
        return Collections.unmodifiableList(imports);
    }
}
