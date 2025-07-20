package it.auties.protobuf.serialization.generator.clazz.group;

import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.generator.clazz.ProtobufClassGenerator;
import it.auties.protobuf.serialization.generator.method.deserialization.group.ProtobufRawGroupDeserializationGenerator;
import it.auties.protobuf.serialization.generator.method.serialization.group.ProtobufRawGroupSerializationGenerator;
import it.auties.protobuf.serialization.generator.method.serialization.group.ProtobufRawGroupSizeGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.writer.CompilationUnitWriter;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.annotation.processing.Filer;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ProtobufRawGroupSpecGenerator extends ProtobufClassGenerator {
    public ProtobufRawGroupSpecGenerator(Filer filer) {
        super(filer);
    }

    public void createClass(ProtobufObjectElement object, PackageElement packageName) throws IOException {
        // Names
        var simpleGeneratedClassName = getGeneratedClassNameBySuffix(object.element(), "Spec");
        var qualifiedGeneratedClassName = packageName != null ? packageName + "." + simpleGeneratedClassName : simpleGeneratedClassName;

        try {
            // Generate a source file
            var sourceFile = filer.createSourceFile(qualifiedGeneratedClassName);

            // Declare a new compilation unit
            try (var compilationUnitWriter = new CompilationUnitWriter(sourceFile.openWriter())) {
                // If a package is available, write it in the compilation unit
                if(packageName != null) {
                    compilationUnitWriter.printPackageDeclaration(packageName.getQualifiedName().toString());
                }

                // Declare the imports needed for everything to work
                var imports = getSpecImports(object.element());
                imports.forEach(compilationUnitWriter::printImportDeclaration);

                // Separate imports from classes
                compilationUnitWriter.printSeparator();

                // Declare the spec class
                try(var classWriter = compilationUnitWriter.printClassDeclaration(simpleGeneratedClassName)) {
                    // Write the serializer
                    var serializationVisitor = new ProtobufRawGroupSerializationGenerator(object);
                    serializationVisitor.generate(classWriter);

                    // Write the deserializer
                    var deserializationVisitor = new ProtobufRawGroupDeserializationGenerator(object);
                    deserializationVisitor.generate(classWriter);

                    // Write the size calculator
                    var sizeVisitor = new ProtobufRawGroupSizeGenerator(object);
                    sizeVisitor.generate(classWriter);
                }
            }
        }catch (IOException ignored) {

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
