package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.ProtobufObjectTypeReference;
import org.junit.Test;
import org.junit.jupiter.api.Nested;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import static org.junit.Assert.*;

@Nested
public class ProtobufParserSemanticTests {
    // 4.1 Type Resolution and Scope
    // 4.1.1 Cross-File Imports and Public Imports
    @Test
    public void testBasicImportResolution() throws IOException {
        // Verify that types defined in an imported file can be used in the current file
        var imported = """
                syntax = "proto3";
                message ImportedType {
                  int32 value = 1;
                }
                """;
        var tempDir = Files.createTempDirectory("protobuf-test");
        var importedFile = tempDir.resolve("imported.proto");
        Files.writeString(importedFile, imported, StandardOpenOption.CREATE);
        var importedDoc = ProtobufParser.parseOnly(importedFile);

        var main = """
                syntax = "proto3";
                import "imported.proto";
                
                message MainType {
                  ImportedType field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(main, importedDoc);
        var message = document.getDirectChildByNameAndType("MainType", ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByNameAndType("field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufObjectTypeReference);
        assertEquals("ImportedType", field.type().name());
    }

    @Test
    public void testPublicImportTransitivity() throws IOException {
        // If file A imports file B public, and file C imports file A, file C should be able to reference types from file B
        var fileB = """
                syntax = "proto3";
                message TypeB {
                  int32 value = 1;
                }
                """;
        var tempDir = Files.createTempDirectory("protobuf-test");
        var fileBPath = tempDir.resolve("b.proto");
        Files.writeString(fileBPath, fileB, StandardOpenOption.CREATE);
        var docB = ProtobufParser.parseOnly(fileBPath);

        var fileA = """
                syntax = "proto3";
                import public "b.proto";
                """;
        var fileAPath = tempDir.resolve("a.proto");
        Files.writeString(fileAPath, fileA, StandardOpenOption.CREATE);
        var docA = ProtobufParser.parseOnly(fileAPath, docB);

        var fileC = """
                syntax = "proto3";
                import "a.proto";
                
                message TypeC {
                  TypeB field = 1;
                }
                """;
        var docC = ProtobufParser.parseOnly(fileC, docA);
        var message = docC.getDirectChildByNameAndType("TypeC", ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByNameAndType("field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufObjectTypeReference);
        assertEquals("TypeB", field.type().name());
    }

    @Test
    public void testNonPublicImportNonTransitivity() throws IOException {
        // If file A imports file B (without public), and file C imports file A, file C should not be able to reference types from file B
        var fileB = """
                syntax = "proto3";
                message TypeB {
                  int32 value = 1;
                }
                """;
        var tempDir = Files.createTempDirectory("protobuf-test");
        var fileBPath = tempDir.resolve("b.proto");
        Files.writeString(fileBPath, fileB, StandardOpenOption.CREATE);
        var docB = ProtobufParser.parseOnly(fileBPath);

        var fileA = """
                syntax = "proto3";
                import "b.proto";
                """;
        var fileAPath = tempDir.resolve("a.proto");
        Files.writeString(fileAPath, fileA, StandardOpenOption.CREATE);
        var docA = ProtobufParser.parseOnly(fileAPath, docB);

        var fileC = """
                syntax = "proto3";
                import "a.proto";
                
                message TypeC {
                  TypeB field = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(fileC, docA));
    }

    @Test
    public void testMissingImportsError() {
        // Ensure an error is reported if a type is referenced but its defining file is not imported
        var main = """
                syntax = "proto3";
                
                message MainType {
                  UndefinedType field = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(main));
    }

    // 4.1.2 Nested Type Resolution
    @Test
    public void testDirectNestingResolution() {
        // message Outer { message Inner {} } - Inner should be resolvable within Outer
        var proto = """
                syntax = "proto3";
                
                message Outer {
                  message Inner {
                    int32 value = 1;
                  }
                
                  Inner field = 2;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var outer = document.getDirectChildByNameAndType("Outer", ProtobufMessageStatement.class).orElseThrow();
        var field = outer.getDirectChildByNameAndType("field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufObjectTypeReference);
        assertEquals("Inner", field.type().name());
    }

    @Test
    public void testQualifiedNestingResolution() {
        // message Outer { message Inner { } } message Another { Outer.Inner nested_field = 1; }
        var proto = """
                syntax = "proto3";
                
                message Outer {
                  message Inner {
                    int32 value = 1;
                  }
                }
                
                message Another {
                  Outer.Inner nested_field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var another = document.getDirectChildByNameAndType("Another", ProtobufMessageStatement.class).orElseThrow();
        var field = another.getDirectChildByNameAndType("nested_field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufObjectTypeReference);
        assertEquals("Inner", field.type().name());
    }

    @Test
    public void testDeepNestingResolution() {
        // Verify resolution for types nested several levels deep
        var proto = """
                syntax = "proto3";
                
                message Level1 {
                  message Level2 {
                    message Level3 {
                      int32 value = 1;
                    }
                  }
                }
                
                message Consumer {
                  Level1.Level2.Level3 deep_field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var consumer = document.getDirectChildByNameAndType("Consumer", ProtobufMessageStatement.class).orElseThrow();
        var field = consumer.getDirectChildByNameAndType("deep_field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufObjectTypeReference);
        assertEquals("Level3", field.type().name());
    }

    // 4.1.3 Fully Qualified Names
    @Test
    public void testGlobalScopeResolution() {
        // The leading . indicates starting resolution from the outermost scope
        var proto = """
                syntax = "proto3";
                package foo.bar;
                
                message MyType {
                  int32 value = 1;
                }
                
                message MyMessage {
                  .foo.bar.MyType field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("MyMessage", ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByNameAndType("field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufObjectTypeReference);
        assertEquals("MyType", field.type().name());
    }

    @Test
    public void testRelativeResolution() {
        // Resolution should start from the innermost scope (current package/message) and proceed outwards
        var proto = """
                syntax = "proto3";
                package test;
                
                message MyType {
                  int32 value = 1;
                }
                
                message MyMessage {
                  MyType field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("MyMessage", ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByNameAndType("field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufObjectTypeReference);
        assertEquals("MyType", field.type().name());
    }

    // 4.1.4 Name Conflicts
    @Test
    public void testFieldFieldConflictsError() {
        // message MyMessage { string a = 1; string a = 2; } - Must be an error
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  string a = 1;
                  string a = 2;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNestedMessageConflictsError() {
        // message MyMessage { string foo = 1; message foo {} } - Must be an error
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  string foo = 1;
                  message foo {
                    int32 x = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldOneofConflictsError() {
        // message MyMessage { string foo = 1; oneof foo { int32 bar = 2; } } - Must be an error
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  string foo = 1;
                  oneof foo {
                    int32 bar = 2;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldEnumConflictsError() {
        // message MyMessage { string foo = 1; enum E { foo = 0; } } - Must be an error
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  string foo = 1;
                  enum E {
                    foo = 0;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedNameConflictsError() {
        // message MyMessage { reserved "my_field"; string my_field = 1; } - Must be an error
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  reserved "my_field";
                  string my_field = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    // 4.2 Field Number Constraints
    @Test
    public void testUniquenessWithinMessage() {
        // Field numbers must be unique within a message
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  string a = 1;
                  int32 b = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedRangesProtobufImplementation() {
        // Ensure fields do not use numbers in the Protobuf implementation's reserved range (19,000-19,999)
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  string field = 19000;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));

        var proto2 = """
                syntax = "proto3";
                
                message MyMessage {
                  string field = 19500;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto2));

        var proto3 = """
                syntax = "proto3";
                
                message MyMessage {
                  string field = 19999;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto3));
    }

    @Test
    public void testUserDefinedReservedNumbers() {
        // Fields must not use numbers explicitly reserved by the user
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  reserved 5, 10 to 20;
                  string field1 = 5;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));

        var proto2 = """
                syntax = "proto3";
                
                message MyMessage {
                  reserved 5, 10 to 20;
                  string field2 = 15;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto2));
    }

    @Test
    public void testOverallRangeLimits() {
        // Numbers must be between 1 and 536,870,911
        var protoZero = """
                syntax = "proto3";
                
                message MyMessage {
                  string field = 0;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoZero));

        var protoTooHigh = """
                syntax = "proto3";
                
                message MyMessage {
                  string field = 536870912;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoTooHigh));

        // Valid boundary cases
        var protoMin = """
                syntax = "proto3";
                
                message MyMessage {
                  string field = 1;
                }
                """;
        ProtobufParser.parseOnly(protoMin);

        var protoMax = """
                syntax = "proto3";
                
                message MyMessage {
                  string field = 536870911;
                }
                """;
        ProtobufParser.parseOnly(protoMax);
    }

    @Test
    public void testConsequencesOfReusePrevention() {
        // The parser's enforcement of reserved keywords prevents reuse issues
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  reserved 1, 2, 3;
                  reserved "old_field";
               
                  string new_field = 4;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        assertEquals(2, message.getDirectChildrenByType(ProtobufReservedStatement.class).count());
    }

    // 4.3 Cardinality and Presence Semantics (Proto2 vs. Proto3)
    @Test
    public void testProto2RequiredFieldEnforcement() {
        // The parser for proto2 must recognize and validate required fields
        var proto = """
                syntax = "proto2";
                
                message MyMessage {
                  required string name = 1;
                  required int32 id = 2;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertSame(ProtobufVersion.PROTOBUF_2, document.syntax().orElse(null));
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var nameField = message.getDirectChildByNameAndType("name", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufFieldStatement.Modifier.REQUIRED, nameField.modifier());
        var idField = message.getDirectChildByNameAndType("id", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufFieldStatement.Modifier.REQUIRED, idField.modifier());
    }

    @Test
    public void testProto3OptionalVsImplicitPresence() {
        // For scalar types, optional keyword enables explicit presence tracking
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  optional string explicit_presence = 1;
                  string implicit_presence = 2;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var explicitField = message.getDirectChildByNameAndType("explicit_presence", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufFieldStatement.Modifier.OPTIONAL, explicitField.modifier());
        var implicitField = message.getDirectChildByNameAndType("implicit_presence", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufFieldStatement.Modifier.NONE, implicitField.modifier());
    }

    @Test
    public void testCompatibilityChecks() {
        // The parser validates compatibility between versions
        var proto2 = """
                syntax = "proto2";
                
                message MyMessage {
                  required string field = 1;
                }
                """;
        ProtobufParser.parseOnly(proto2);

        // Proto3 doesn't support required
        var proto3 = """
                syntax = "proto3";
                
                message MyMessage {
                  required string field = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto3));
    }

    // 4.4 Enum Semantics
    // 4.4.1 Zero Value Requirement
    @Test
    public void testFirstEnumValueMustBeZero() {
        // The first value in an enum definition must be zero
        var protoValid = """
                syntax = "proto3";
                
                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 1;
                }
                """;
        ProtobufParser.parseOnly(protoValid);

        var protoInvalid = """
                syntax = "proto3";
                
                enum Status {
                  ACTIVE = 1;
                  UNKNOWN = 0;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoInvalid));
    }

    @Test
    public void testZeroValueNamedUnknownOrUnspecifiedRecommendation() {
        // The zero value should ideally be named UNKNOWN or UNSPECIFIED
        var protoUnknown = """
                syntax = "proto3";
                
                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 1;
                }
                """;
        var doc1 = ProtobufParser.parseOnly(protoUnknown);
        var enum1 = doc1.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        var constant1 = enum1.getDirectChildByNameAndType("UNKNOWN", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(BigInteger.ZERO, constant1.index().value());

        var protoUnspecified = """
                syntax = "proto3";
                
                enum Status {
                  STATUS_UNSPECIFIED = 0;
                  ACTIVE = 1;
                }
                """;
        var doc2 = ProtobufParser.parseOnly(protoUnspecified);
        var enum2 = doc2.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        var constant2 = enum2.getDirectChildByNameAndType("STATUS_UNSPECIFIED", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(BigInteger.ZERO, constant2.index().value());
    }

    // 4.4.2 Alias Handling
    @Test
    public void testAllowAliasOption() {
        // With allow_alias option, multiple enum constants can have the same numeric value
        var proto = """
                syntax = "proto3";
                
                enum Status {
                  option allow_alias = true;
                  UNKNOWN = 0;
                  STARTED = 1;
                  RUNNING = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var enumStmt = document.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        var option = enumStmt.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("allow_alias", option.name().toString());
        var started = enumStmt.getDirectChildByNameAndType("STARTED", ProtobufEnumConstantStatement.class).orElseThrow();
        var running = enumStmt.getDirectChildByNameAndType("RUNNING", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(started.index(), running.index());
    }

    @Test
    public void testAliasWithoutOptionWarningOrError() {
        // Aliases defined without allow_alias = true should cause an error
        var proto = """
                syntax = "proto3";
                
                enum Status {
                  UNKNOWN = 0;
                  STARTED = 1;
                  RUNNING = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    // 4.4.3 Reserved Enum Values
    @Test
    public void testReservedEnumValuesEnforcement() {
        // Enum numeric values and names can be reserved to prevent reuse
        var proto = """
                syntax = "proto3";
                
                enum Status {
                  reserved 2, 15, 9 to 11;
                  reserved "FOO", "BAR";
                
                  UNKNOWN = 0;
                  ACTIVE = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var enumStmt = document.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        assertEquals(2, enumStmt.getDirectChildrenByType(ProtobufReservedStatement.class).count());

        // Test violation of reserved number
        var protoViolateNumber = """
                syntax = "proto3";
                
                enum Status {
                  reserved 2;
                  UNKNOWN = 0;
                  ACTIVE = 2;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoViolateNumber));

        // Test violation of reserved name
        var protoViolateName = """
                syntax = "proto3";
                
                enum Status {
                  reserved "FOO";
                  UNKNOWN = 0;
                  FOO = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoViolateName));
    }

    // 4.5 Oneof Semantics
    @Test
    public void testOneofNoRepeatedOrMapFields() {
        // oneof fields cannot contain repeated or map types directly
        var protoRepeated = """
                syntax = "proto3";
                
                message MyMessage {
                  oneof choice {
                    repeated int32 numbers = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoRepeated));

        var protoMap = """
                syntax = "proto3";
                
                message MyMessage {
                  oneof choice {
                    map<string, int32> data = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoMap));
    }

    @Test
    public void testOneofFieldNumbersUniqueWithinMessage() {
        // Field numbers within a oneof block must be unique within the enclosing message
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  oneof choice {
                    string name = 1;
                    int32 id = 2;
                  }
                
                  string other = 3;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var oneof = message.getDirectChildByType(ProtobufOneofFieldStatement.class).orElseThrow();
        assertEquals(2, oneof.children().size());

        // Test duplicate field number
        var protoDuplicate = """
                syntax = "proto3";
                
                message MyMessage {
                  oneof choice {
                    string name = 1;
                    int32 id = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoDuplicate));
    }

    @Test
    public void testOneofNoCardinalityLabels() {
        // oneof fields should not have optional, required, or repeated labels
        var protoOptional = """
                syntax = "proto3";
                
                message MyMessage {
                  oneof choice {
                    optional string name = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoOptional));

        var protoRepeated = """
                syntax = "proto3";
                
                message MyMessage {
                  oneof choice {
                    repeated string names = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoRepeated));
    }

    // 4.6 Map Semantics
    @Test
    public void testMapKeyTypeRestrictions() {
        // Keys must be integral or string types; floating-point, bytes, enum, or message types are disallowed
        var protoValid = """
                syntax = "proto3";
                
                message MyMessage {
                  map<int32, string> valid1 = 1;
                  map<string, int32> valid2 = 2;
                  map<uint64, string> valid3 = 3;
                }
                """;
        ProtobufParser.parseOnly(protoValid);

        var protoFloat = """
                syntax = "proto3";
                
                message MyMessage {
                  map<float, string> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoFloat));

        var protoDouble = """
                syntax = "proto3";
                
                message MyMessage {
                  map<double, string> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoDouble));

        var protoBytes = """
                syntax = "proto3";
                
                message MyMessage {
                  map<bytes, string> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoBytes));
    }

    @Test
    public void testMapValueTypeRestrictions() {
        // Values can be any type except another map
        var protoValid = """
                syntax = "proto3";
                
                message Value {
                  int32 x = 1;
                }
                
                message MyMessage {
                  map<string, int32> valid1 = 1;
                  map<string, Value> valid2 = 2;
                }
                """;
        ProtobufParser.parseOnly(protoValid);

        var protoInvalid = """
                syntax = "proto3";
                
                message MyMessage {
                  map<string, map<int32, string>> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoInvalid));
    }

    @Test
    public void testMapNoCardinalityLabels() {
        // Map fields cannot be repeated, optional, or required
        var protoRepeated = """
                syntax = "proto3";
                
                message MyMessage {
                  repeated map<string, int32> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoRepeated));

        var protoOptional = """
                syntax = "proto3";
                
                message MyMessage {
                  optional map<string, int32> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoOptional));

        var protoRequired = """
                syntax = "proto2";
                
                message MyMessage {
                  required map<string, int32> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoRequired));
    }

    // 4.7 Option Semantics (Custom Options, Retention, Targets)
    @Test
    public void testCustomOptionDefinitionAndUsage() {
        // Verify that custom options are defined and used correctly
        var proto = """
                syntax = "proto3";
                
                message MyOption {
                  string value = 1;
                }
                
                extend google.protobuf.MessageOptions {
                  MyOption my_message_option = 50000;
                }
                
                extend google.protobuf.FieldOptions {
                  string my_field_option = 50001;
                }
                
                message MyMessage {
                  option (my_message_option).value = "test";
                  string field = 1 [(my_field_option) = "field_value"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("MyMessage", ProtobufMessageStatement.class).orElseThrow();
        assertTrue(message.getDirectChildByType(ProtobufOptionStatement.class).isPresent());
    }

    @Test
    public void testOptionRetentionRecognition() {
        // The parser should recognize the retention option
        var proto = """
                syntax = "proto3";
                
                extend google.protobuf.FieldOptions {
                  string my_option = 50000 [retention = RETENTION_SOURCE];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var extend = document.getDirectChildByType(ProtobufExtendStatement.class).orElseThrow();
        var field = extend.getDirectChildByType(ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.options().stream()
                .anyMatch(opt -> opt.name().toString().equals("retention")));
    }

    @Test
    public void testOptionTargetsValidation() {
        // The parser should validate targets options
        var proto = """
                syntax = "proto3";
                
                extend google.protobuf.FieldOptions {
                  string my_option = 50000 [targets = TARGET_TYPE_FIELD];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var extend = document.getDirectChildByType(ProtobufExtendStatement.class).orElseThrow();
        var field = extend.getDirectChildByType(ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.options().stream()
                .anyMatch(opt -> opt.name().toString().equals("targets")));
    }

    // 4.8 Well-Known Types
    @Test
    public void testBasicWellKnownTypeUsage() {
        // Test basic usage of well-known types
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  google.protobuf.Timestamp timestamp = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByNameAndType("timestamp", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufObjectTypeReference);
        var typeRef = (ProtobufObjectTypeReference) field.type();
        assertEquals("google.protobuf.Timestamp", typeRef.name());
    }

    @Test
    public void testWrapperTypesRecognition() {
        // Test wrapper types recognition
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  google.protobuf.BoolValue bool_field = 1;
                  google.protobuf.Int32Value int_field = 2;
                  google.protobuf.StringValue string_field = 3;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();

        var boolField = message.getDirectChildByNameAndType("bool_field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(boolField.type() instanceof ProtobufObjectTypeReference);
        assertEquals("google.protobuf.BoolValue", boolField.type().name());

        var intField = message.getDirectChildByNameAndType("int_field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(intField.type() instanceof ProtobufObjectTypeReference);
        assertEquals("google.protobuf.Int32Value", intField.type().name());

        var stringField = message.getDirectChildByNameAndType("string_field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(stringField.type() instanceof ProtobufObjectTypeReference);
        assertEquals("google.protobuf.StringValue", stringField.type().name());
    }

    @Test
    public void testAnyTypeRecognition() {
        // Test Any type recognition
        var proto = """
                syntax = "proto3";
                
                message MyMessage {
                  google.protobuf.Any data = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByNameAndType("data", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufObjectTypeReference);
        assertEquals("google.protobuf.Any", field.type().name());
    }

    @Test
    public void testJsonRepresentationImplicationsOfWKTs() {
        // Parser should recognize well-known types that have special JSON representations
        var proto = """
                syntax = "proto3";
                
                import "google/protobuf/timestamp.proto";
                import "google/protobuf/duration.proto";
                import "google/protobuf/any.proto";
                import "google/protobuf/struct.proto";
                
                message MyMessage {
                  google.protobuf.Timestamp timestamp = 1;
                  google.protobuf.Duration duration = 2;
                  google.protobuf.Any any = 3;
                  google.protobuf.Struct struct = 4;
                  google.protobuf.Value value = 5;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();

        // Verify all fields are parsed correctly
        assertEquals(5, message.getDirectChildrenByType(ProtobufFieldStatement.class).count());

        // Verify each type is recognized as an object type reference
        var timestamp = message.getDirectChildByNameAndType("timestamp", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(timestamp.type() instanceof ProtobufObjectTypeReference);
        assertEquals("Timestamp", timestamp.type().name());

        var duration = message.getDirectChildByNameAndType("duration", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(duration.type() instanceof ProtobufObjectTypeReference);
        assertEquals("Duration", duration.type().name());
    }
}