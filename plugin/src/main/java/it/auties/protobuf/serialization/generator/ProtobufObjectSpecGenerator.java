package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;
import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement.Type;
import it.auties.protobuf.serialization.model.ProtobufPropertyElement;
import it.auties.protobuf.io.ProtobufReader;
import it.auties.protobuf.io.ProtobufWriter;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import java.io.IOException;
import java.util.*;

// Main orchestrator that generates complete Spec classes for protobuf objects
//
// Example Input:
//   @ProtobufMessage
//   public record Person(
//       @ProtobufProperty(index = 1) String name,
//       @ProtobufProperty(index = 2) int age
//   ) {}
//
// Example Output (PersonSpec.java):
//   public class PersonSpec {
//       // Overload: byte[] -> Person
//       public static Person decode(byte[] protoInputObject) { ... }
//
//       // Main deserializer: ProtobufInputStream -> Person
//       public static Person decode(ProtobufInputStream protoInputStream) { ... }
//
//       // Overload: Person -> byte[]
//       public static byte[] encode(Person protoInputObject) { ... }
//
//       // Main serializer: Person -> void (writes to stream)
//       public static void encode(Person protoInputObject, ProtobufOutputStream protoOutputStream) { ... }
//
//       // Size calculator: Person -> int
//       public static int sizeOf(Person protoInputObject) { ... }
//   }
//
// For Enums, also generates:
//   private static final Map<Integer, Status> VALUES = new HashMap<>();
//   static {
//       VALUES.put(0, Status.ACTIVE);
//       VALUES.put(1, Status.INACTIVE);
//   }
//
// Execution Flow:
//   1. Create TypeSpec.Builder for the Spec class
//   2. For enums:
//      a. Create static VALUES field (Map<Integer, EnumType>)
//      b. Populate VALUES in static initializer block
//   3. Generate all methods using specialized generators:
//      a. ProtobufObjectSerializationOverloadGenerator - encode(object) -> byte[]
//      b. ProtobufObjectSerializationGenerator - encode(object, stream)
//      c. ProtobufObjectDeserializationOverloadGenerator - decode(byte[]) -> object
//      d. ProtobufObjectDeserializationGenerator - decode(stream) -> object
//      e. ProtobufObjectSizeGenerator - sizeOf(object) -> int
//   4. Build TypeSpec and write to JavaFile
//   5. Write JavaFile to Filer (generates .java source file)
public class ProtobufObjectSpecGenerator extends ProtobufClassGenerator {
    public ProtobufObjectSpecGenerator(Filer filer) {
        super(filer);
    }

    public void createClass(ProtobufObjectElement objectElement, PackageElement packageElement) throws IOException {
        // Names
        var simpleGeneratedClassName = getGeneratedClassNameBySuffix(objectElement.typeElement(), "Spec");
        var packageName = packageElement != null ? packageElement.getQualifiedName().toString() : "";

        // Create the class
        var classBuilder = TypeSpec.classBuilder(simpleGeneratedClassName)
                .addModifiers(Modifier.PUBLIC);

        if(objectElement.type() == Type.ENUM) {
            var objectType = objectElement.typeElement().getSimpleName().toString();
            var objectClassName = ClassName.get(objectElement.typeElement());
            var mapType = ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(Integer.class), objectClassName);

            var valuesField = FieldSpec.builder(mapType, ProtobufObjectDeserializationGenerator.ENUM_VALUES_FIELD)
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T<>()", HashMap.class)
                    .build();
            classBuilder.addField(valuesField);

            var staticBlock = com.palantir.javapoet.CodeBlock.builder();
            for(var entry : objectElement.constants().entrySet()) {
                staticBlock.addStatement("$L.put($L, $L.$L)",
                        ProtobufObjectDeserializationGenerator.ENUM_VALUES_FIELD,
                        entry.getKey(),
                        objectType,
                        entry.getValue());
            }
            classBuilder.addStaticBlock(staticBlock.build());
        }

        // Write the serializer
        var serializationOverloadVisitor = new ProtobufObjectSerializationOverloadGenerator(objectElement);
        serializationOverloadVisitor.generate(classBuilder);
        var serializationVisitor = new ProtobufObjectSerializationGenerator(objectElement);
        serializationVisitor.generate(classBuilder);

        // Write the deserializer
        var deserializationOverloadVisitor = new ProtobufObjectDeserializationOverloadGenerator(objectElement);
        deserializationOverloadVisitor.generate(classBuilder);
        var deserializationVisitor = new ProtobufObjectDeserializationGenerator(objectElement);
        deserializationVisitor.generate(classBuilder);

        // Write the size calculator
        var sizeVisitor = new ProtobufObjectSizeGenerator(objectElement);
        sizeVisitor.generate(classBuilder);

        // Write the file
        var javaFile = JavaFile.builder(packageName, classBuilder.build())
                .build();
        javaFile.writeTo(filer);
    }

    // Get the imports to include in the compilation unit
    private List<String> getSpecImports(ProtobufObjectElement message) {
        if(message.type() == Type.ENUM) {
            return List.of(
                    message.typeElement().getQualifiedName().toString(),
                    Arrays.class.getName(),
                    Optional.class.getName(),
                    ProtobufWriter.class.getName(),
                    Map.class.getName(),
                    HashMap.class.getName()
            );
        }

        var imports = new ArrayList<String>();
        imports.add(message.typeElement().getQualifiedName().toString());
        imports.add(ProtobufReader.class.getName());
        imports.add(ProtobufWriter.class.getName());
        imports.add(ProtobufWireType.class.getName());
        if (message.properties().stream().anyMatch(ProtobufPropertyElement::required)) {
            imports.add(Objects.class.getName());
        }

        return Collections.unmodifiableList(imports);
    }
}
