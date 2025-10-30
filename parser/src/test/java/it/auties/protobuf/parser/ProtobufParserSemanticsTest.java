package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.ProtobufObjectTypeReference;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

public class ProtobufParserSemanticsTest {
    @Test
    public void testBasicImportResolution() throws IOException {
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
        assertInstanceOf(ProtobufObjectTypeReference.class, field.type());
        assertEquals("ImportedType", field.type().name());
    }

    @Test
    public void testPublicImportTransitivity() throws IOException {
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
        assertInstanceOf(ProtobufObjectTypeReference.class, field.type());
        assertEquals("TypeB", field.type().name());
    }

    @Test
    public void testNonPublicImportNonTransitivity() throws IOException {
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
        var main = """
                    syntax = "proto3";
                    
                    message MainType {
                      UndefinedType field = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(main));
    }

    @Test
    public void testDirectNestingResolution() {
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
        assertInstanceOf(ProtobufObjectTypeReference.class, field.type());
        assertEquals("Outer.Inner", field.type().name());
    }

    @Test
    public void testQualifiedNestingResolution() {
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
        assertInstanceOf(ProtobufObjectTypeReference.class, field.type());
        assertEquals("Outer.Inner", field.type().name());
    }

    @Test
    public void testDeepNestingResolution() {
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
        assertInstanceOf(ProtobufObjectTypeReference.class, field.type());
        assertEquals("Level1.Level2.Level3", field.type().name());
    }

    @Test
    public void testGlobalScopeResolution() {
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
        assertInstanceOf(ProtobufObjectTypeReference.class, field.type());
        assertEquals("foo.bar.MyType", field.type().name());
    }

    @Test
    public void testRelativeResolution() {
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
        assertInstanceOf(ProtobufObjectTypeReference.class, field.type());
        assertEquals("test.MyType", field.type().name());
    }

    @Test
    public void testFieldFieldConflictsError() {
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
        var proto = """
                    syntax = "proto3";
                    
                    message MyMessage {
                      reserved "my_field";
                      string my_field = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testUniquenessWithinMessage() {
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

    @Test
    public void testProto2RequiredFieldEnforcement() {
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
        var proto2 = """
                    syntax = "proto2";
                    
                    message MyMessage {
                      required string field = 1;
                    }
                    """;
        ProtobufParser.parseOnly(proto2);

        var proto3 = """
                    syntax = "proto3";
                    
                    message MyMessage {
                      required string field = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto3));
    }

    @Test
    public void testFirstEnumValueMustBeZero() {
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

    @Test
    public void testAllowAliasOption() {
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

    @Test
    public void testReservedEnumValuesEnforcement() {
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

        var protoViolateNumber = """
                    syntax = "proto3";
                    
                    enum Status {
                      reserved 2;
                      UNKNOWN = 0;
                      ACTIVE = 2;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoViolateNumber));

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

    @Test
    public void testOneofNoRepeatedOrMapFields() {
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

    @Test
    public void testMapKeyTypeRestrictions() {
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

    @Test
    public void testCustomOptionDefinitionAndUsage() {
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

    @Test
    public void testBasicWellKnownTypeUsage() {
        var proto = """
                    syntax = "proto3";
                    
                    message MyMessage {
                      google.protobuf.Timestamp timestamp = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByNameAndType("timestamp", ProtobufFieldStatement.class).orElseThrow();
        assertInstanceOf(ProtobufObjectTypeReference.class, field.type());
        var typeRef = (ProtobufObjectTypeReference) field.type();
        assertEquals("google.protobuf.Timestamp", typeRef.name());
    }

    @Test
    public void testWrapperTypesRecognition() {
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
        assertInstanceOf(ProtobufObjectTypeReference.class, boolField.type());
        assertEquals("google.protobuf.BoolValue", boolField.type().name());

        var intField = message.getDirectChildByNameAndType("int_field", ProtobufFieldStatement.class).orElseThrow();
        assertInstanceOf(ProtobufObjectTypeReference.class, intField.type());
        assertEquals("google.protobuf.Int32Value", intField.type().name());

        var stringField = message.getDirectChildByNameAndType("string_field", ProtobufFieldStatement.class).orElseThrow();
        assertInstanceOf(ProtobufObjectTypeReference.class, stringField.type());
        assertEquals("google.protobuf.StringValue", stringField.type().name());
    }

    @Test
    public void testAnyTypeRecognition() {
        var proto = """
                    syntax = "proto3";
                    
                    message MyMessage {
                      google.protobuf.Any data = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByNameAndType("data", ProtobufFieldStatement.class).orElseThrow();
        assertInstanceOf(ProtobufObjectTypeReference.class, field.type());
        assertEquals("google.protobuf.Any", field.type().name());
    }

    @Test
    public void testJsonRepresentationImplicationsOfWKTs() {
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

        assertEquals(5, message.getDirectChildrenByType(ProtobufFieldStatement.class).count());

        var timestamp = message.getDirectChildByNameAndType("timestamp", ProtobufFieldStatement.class).orElseThrow();
        assertInstanceOf(ProtobufObjectTypeReference.class, timestamp.type());
        assertEquals("google.protobuf.Timestamp", timestamp.type().name());

        var duration = message.getDirectChildByNameAndType("duration", ProtobufFieldStatement.class).orElseThrow();
        assertInstanceOf(ProtobufObjectTypeReference.class, duration.type());
        assertEquals("google.protobuf.Duration", duration.type().name());
    }

    @Test
    public void testBasicExtensionRangeDeclaration() {
        var proto = """
                    syntax = "proto2";
    
                    message Extendable {
                      extensions 100 to 199;
                      optional string name = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        assertEquals("Extendable", message.name());
    }

    @Test
    public void testExtensionRangeWithMax() {
        var proto = """
                    syntax = "proto2";
    
                    message Extendable {
                      extensions 1000 to max;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMultipleExtensionRanges() {
        var proto = """
                    syntax = "proto2";
    
                    message Extendable {
                      extensions 100 to 199, 500, 1000 to 2000;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testExtensionFieldDefinition() {
        var proto = """
                    syntax = "proto2";
    
                    message Extendable {
                      extensions 100 to 199;
                    }
    
                    extend Extendable {
                      optional string custom_field = 100;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        var extend = document.getDirectChildByType(ProtobufExtendStatement.class).orElseThrow();
        assertEquals("Extendable", extend.declaration().name());
        var field = extend.getDirectChildByType(ProtobufFieldStatement.class).orElseThrow();
        assertEquals("custom_field", field.name());
        assertEquals(100L, field.index().value().longValue());
    }

    @Test
    public void testExtensionOutsideDeclaredRangeError() {
        var proto = """
                    syntax = "proto2";
    
                    message Extendable {
                      extensions 100 to 199;
                    }
    
                    extend Extendable {
                      optional string invalid_field = 200;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionWithoutRangeDeclarationError() {
        var proto = """
                    syntax = "proto2";
    
                    message NotExtendable {
                      optional string name = 1;
                    }
    
                    extend NotExtendable {
                      optional string ext = 100;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionCannotBeMapField() {
        var proto = """
                    syntax = "proto2";
    
                    message Extendable {
                      extensions 100 to 199;
                    }
    
                    extend Extendable {
                      map<string, int32> invalid = 100;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionCannotBeOneofField() {
        var proto = """
                    syntax = "proto2";
    
                    message Extendable {
                      extensions 100 to 199;
                    }
    
                    extend Extendable {
                      oneof choice {
                        string a = 100;
                        int32 b = 101;
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionCannotBeRequiredField() {
        var proto = """
                    syntax = "proto2";
    
                    message Extendable {
                      extensions 100 to 199;
                    }
    
                    extend Extendable {
                      required string invalid = 100;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionRangeOverlapWithFieldNumbersError() {
        var proto = """
                    syntax = "proto2";
    
                    message Invalid {
                      optional string field = 100;
                      extensions 100 to 199;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionRangeOverlapWithReservedError() {
        var proto = """
                    syntax = "proto2";
    
                    message Invalid {
                      reserved 150 to 200;
                      extensions 100 to 199;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionsNotAllowedInProto3MessagesError() {
        var proto = """
                    syntax = "proto3";
    
                    message Invalid {
                      extensions 100 to 199;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testProto3CanExtendWellKnownTypes() {
        var proto = """
                    syntax = "proto3";
    
                    extend google.protobuf.FileOptions {
                      string my_file_option = 50000;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        var extend = document.getDirectChildByType(ProtobufExtendStatement.class).orElseThrow();
        assertEquals("google.protobuf.FileOptions", extend.declaration().name());
    }

    @Test
    public void testReservedRangesCannotOverlap() {
        var proto = """
                    syntax = "proto3";
    
                    message Invalid {
                      reserved 10 to 20, 15 to 25;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedRangeInvalidOrder() {
        var proto = """
                    syntax = "proto3";
    
                    message Invalid {
                      reserved 20 to 10;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedWithMaxKeyword() {
        var proto = """
                    syntax = "proto3";
    
                    message M {
                      reserved 1000 to max;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testReservedNumbersInProtobufImplementationRange() {
        var proto = """
                    syntax = "proto3";
    
                    message M {
                      reserved 19000, 19500, 19999;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testReservedNumberZeroError() {
        var proto = """
                    syntax = "proto3";
    
                    message Invalid {
                      reserved 0;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedNumberAboveMaxError() {
        var proto = """
                    syntax = "proto3";
    
                    message Invalid {
                      reserved 536870912;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testCannotMixReservedNumbersAndNames() {
        var proto = """
                    syntax = "proto3";
    
                    message Invalid {
                      reserved 1, "foo", 2;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumValueNegativeNumbers() {
        var proto = """
                    syntax = "proto3";
    
                    enum E {
                      UNKNOWN = 0;
                      NEGATIVE = -1;
                      LARGE_NEGATIVE = -2147483648;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        var enumStmt = document.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        var negative = enumStmt.getDirectChildByNameAndType("LARGE_NEGATIVE", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(BigInteger.valueOf(-2147483648L), negative.index().value());
    }

    @Test
    public void testEnumValueMaxInt32() {
        var proto = """
                    syntax = "proto3";
    
                    enum E {
                      UNKNOWN = 0;
                      MAX = 2147483647;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        var enumStmt = document.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        var max = enumStmt.getDirectChildByNameAndType("MAX", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(BigInteger.valueOf(2147483647L), max.index().value());
    }

    @Test
    public void testEnumValueMinInt32() {
        var proto = """
                    syntax = "proto3";
    
                    enum E {
                      UNKNOWN = 0;
                      MIN = -2147483648;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testEnumValueOutsideInt32RangeError() {
        var proto = """
                    syntax = "proto3";
    
                    enum E {
                      UNKNOWN = 0;
                      TOO_LARGE = 2147483648;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));

        var proto2 = """
                    syntax = "proto3";
    
                    enum E {
                      UNKNOWN = 0;
                      TOO_SMALL = -2147483649;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto2));
    }

    @Test
    public void testEnumDuplicateValueWithoutAllowAliasError() {
        var proto = """
                    syntax = "proto3";
    
                    enum E {
                      A = 0;
                      B = 1;
                      C = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumDuplicateNameError() {
        var proto = """
                    syntax = "proto3";
    
                    enum E {
                      DUP = 0;
                      DUP = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumReservedValueViolation() {
        var proto = """
                    syntax = "proto3";
    
                    enum E {
                      reserved 5, 10 to 20;
                      UNKNOWN = 0;
                      INVALID = 15;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumCannotBeMapKey() {
        var proto = """
                    syntax = "proto3";
    
                    enum E { UNKNOWN = 0; A = 1; }
    
                    message M {
                      map<E, string> invalid = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumCanBeMapValue() {
        var proto = """
                    syntax = "proto3";

                    enum E { UNKNOWN = 0; A = 1; }

                    message M {
                      map<string, E> valid = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testEnumInOneof() {
        var proto = """
                    syntax = "proto3";

                    enum E { UNKNOWN = 0; A = 1; }

                    message M {
                      oneof choice {
                        E enum_val = 1;
                        string str_val = 2;
                      }
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testOneofWithMessageTypes() {
        var proto = """
                    syntax = "proto3";
    
                    message Sub {
                      int32 x = 1;
                    }
    
                    message M {
                      oneof choice {
                        Sub msg = 1;
                        string str = 2;
                      }
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testOneofWithWellKnownTypes() {
        var proto = """
                    syntax = "proto3";
    
                    message M {
                      oneof choice {
                        google.protobuf.Timestamp timestamp = 1;
                        google.protobuf.Duration duration = 2;
                      }
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testEmptyOneofError() {
        var proto = """
                    syntax = "proto3";
    
                    message M {
                      oneof empty {
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testNestedOneofError() {
        var proto = """
                    syntax = "proto3";
    
                    message M {
                      oneof outer {
                        oneof inner {
                          string a = 1;
                        }
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapWithAllValidIntegralKeyTypes() {
        var proto = """
                    syntax = "proto3";
    
                    message M {
                      map<int32, string> m1 = 1;
                      map<int64, string> m2 = 2;
                      map<uint32, string> m3 = 3;
                      map<uint64, string> m4 = 4;
                      map<sint32, string> m5 = 5;
                      map<sint64, string> m6 = 6;
                      map<fixed32, string> m7 = 7;
                      map<fixed64, string> m8 = 8;
                      map<sfixed32, string> m9 = 9;
                      map<sfixed64, string> m10 = 10;
                      map<bool, string> m11 = 11;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMapWithMessageValue() {
        var proto = """
                    syntax = "proto3";
    
                    message Value {
                      int32 x = 1;
                    }
    
                    message M {
                      map<string, Value> data = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMapWithEnumValue() {
        var proto = """
                    syntax = "proto3";
    
                    enum E { UNKNOWN = 0; A = 1; }
    
                    message M {
                      map<string, E> data = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMapWithMessageKeyError() {
        var proto = """
                    syntax = "proto3";

                    message Key {
                      int32 x = 1;
                    }

                    message M {
                      map<Key, string> invalid = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    // Additional comprehensive semantics tests for full spec compliance

    @Test
    public void testForwardTypeReference() {
        var proto = """
                    syntax = "proto3";

                    message A {
                      B b_field = 1;
                    }

                    message B {
                      int32 value = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testTypeResolutionWithSiblingScope() {
        var proto = """
                    syntax = "proto3";

                    message Sibling1 {
                      int32 x = 1;
                    }

                    message Sibling2 {
                      Sibling1 field = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testNestedTypeVisibilityFromParent() {
        var proto = """
                    syntax = "proto3";

                    message Outer {
                      message Inner {
                        int32 value = 1;
                      }
                      Inner field = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testNestedTypeVisibilityAcrossSiblings() {
        var proto = """
                    syntax = "proto3";

                    message Outer {
                      message Inner1 {
                        int32 x = 1;
                      }
                      message Inner2 {
                        Inner1 field = 1;
                      }
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testAmbiguousTypeReferenceResolution() {
        var proto = """
                    syntax = "proto3";

                    message Type {
                      int32 global = 1;
                    }

                    message Container {
                      message Type {
                        int32 local = 1;
                      }
                      Type field = 1;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        var container = document.getDirectChildByNameAndType("Container", ProtobufMessageStatement.class).orElseThrow();
        var field = container.getDirectChildByNameAndType("field", ProtobufFieldStatement.class).orElseThrow();
        // Should resolve to the local Container.Type
        assertNotNull(field);
    }

    @Test
    public void testExtensionFieldCannotBeRequired() {
        var proto = """
                    syntax = "proto2";

                    message M {
                      extensions 100 to 200;
                    }

                    extend M {
                      required string invalid = 100;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedNumberConflictWithFieldNumber() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      reserved 5, 10 to 20;
                      string field = 15;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedNameConflictWithFieldName() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      reserved "foo", "bar";
                      string foo = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumReservedNumberConflictWithValue() {
        var proto = """
                    syntax = "proto3";

                    enum E {
                      reserved 10, 20 to 30;
                      ZERO = 0;
                      INVALID = 25;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumReservedNameConflictWithConstant() {
        var proto = """
                    syntax = "proto3";

                    enum E {
                      reserved "FOO", "BAR";
                      ZERO = 0;
                      FOO = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDefaultValueOnNonOptionalFieldProto2Error() {
        var proto = """
                    syntax = "proto2";

                    message M {
                      required string name = 1 [default = "test"];
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDefaultValueOutOfRangeInt32() {
        var proto = """
                    syntax = "proto2";

                    message M {
                      optional int32 num = 1 [default = 2147483648];
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDefaultValueEnumConstantDoesNotExist() {
        var proto = """
                    syntax = "proto2";

                    enum E {
                      UNKNOWN = 0;
                      ACTIVE = 1;
                    }

                    message M {
                      optional E status = 1 [default = NONEXISTENT];
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackedOptionOnNonPackableType() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      repeated string names = 1 [packed = true];
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackedOptionOnNonRepeatedField() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      int32 num = 1 [packed = true];
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapWithBytesKey() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      map<bytes, int32> invalid = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOneofFieldNumberConflictWithRegularField() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      string regular = 1;
                      oneof choice {
                        int32 option = 1;
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOneofNameConflictWithField() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      string choice = 1;
                      oneof choice {
                        int32 a = 2;
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNameConflictWithEnumValue() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      string ACTIVE = 1;
                      enum Status {
                        UNKNOWN = 0;
                        ACTIVE = 1;
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumValueNameConflictWithinEnum() {
        var proto = """
                    syntax = "proto3";

                    enum E {
                      UNKNOWN = 0;
                      ACTIVE = 1;
                      UNKNOWN = 2;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDuplicateMessageNames() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      int32 x = 1;
                    }

                    message M {
                      int32 y = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDuplicateEnumNames() {
        var proto = """
                    syntax = "proto3";

                    enum E {
                      ZERO = 0;
                    }

                    enum E {
                      ONE = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDuplicateServiceNames() {
        var proto = """
                    syntax = "proto3";

                    message Req {}
                    message Res {}

                    service S {
                      rpc M1(Req) returns (Res);
                    }

                    service S {
                      rpc M2(Req) returns (Res);
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testGroupWithReservedFieldNumber() {
        var proto = """
                    syntax = "proto2";

                    message M {
                      reserved 1;
                      optional group G = 1 {
                        optional int32 x = 2;
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionRangeContainsReservedNumber() {
        var proto = """
                    syntax = "proto2";

                    message M {
                      reserved 100 to 150;
                      extensions 120 to 200;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testRepeatedOneofFieldError() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      oneof choice {
                        repeated int32 nums = 1;
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOptionalOneofFieldError() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      oneof choice {
                        optional int32 num = 1;
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapInOneofFieldError() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      oneof choice {
                        map<string, int32> data = 1;
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testProto3EnumFirstValueNotZero() {
        var proto = """
                    syntax = "proto3";

                    enum E {
                      ONE = 1;
                      TWO = 2;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumValueAliasWithoutOption() {
        var proto = """
                    syntax = "proto3";

                    enum E {
                      ZERO = 0;
                      ALIAS = 0;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumValueAliasWithOption() {
        var proto = """
                    syntax = "proto3";

                    enum E {
                      option allow_alias = true;
                      ZERO = 0;
                      ALIAS = 0;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMapValueCannotBeMap() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      map<string, map<int32, string>> nested = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testAllValidMapKeyTypes() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      map<int32, string> m1 = 1;
                      map<int64, string> m2 = 2;
                      map<uint32, string> m3 = 3;
                      map<uint64, string> m4 = 4;
                      map<sint32, string> m5 = 5;
                      map<sint64, string> m6 = 6;
                      map<fixed32, string> m7 = 7;
                      map<fixed64, string> m8 = 8;
                      map<sfixed32, string> m9 = 9;
                      map<sfixed64, string> m10 = 10;
                      map<bool, string> m11 = 11;
                      map<string, string> m12 = 12;
                    }
                    """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMapWithFloatKeyError() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      map<float, string> invalid = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapWithDoubleKeyError() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      map<double, string> invalid = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testServiceMethodDuplicateNames() {
        var proto = """
                    syntax = "proto3";

                    message Req {}
                    message Res {}

                    service S {
                      rpc Method(Req) returns (Res);
                      rpc Method(Req) returns (Res);
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionInProto3() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      extensions 100 to 200;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDefaultValueInProto3() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      int32 num = 1 [default = 42];
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testRequiredFieldInProto3() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      required int32 num = 1;
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testGroupInProto3() {
        var proto = """
                    syntax = "proto3";

                    message M {
                      optional group G = 1 {
                        int32 x = 2;
                      }
                    }
                    """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }
}
