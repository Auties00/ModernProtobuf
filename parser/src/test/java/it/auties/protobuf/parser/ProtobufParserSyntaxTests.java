package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.ProtobufMapTypeReference;
import it.auties.protobuf.parser.type.ProtobufPrimitiveTypeReference;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.Nested;

import java.nio.file.Path;

import static org.junit.Assert.*;

@Nested
public class ProtobufParserSyntaxTests {
    @Test
    public void testValidProto2SyntaxDeclaration() {
        var proto = """
                syntax = "proto2";
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertSame(ProtobufVersion.PROTOBUF_2, document.syntax().orElse(null));
    }

    @Test
    public void testValidProto3SyntaxDeclaration() {
        var proto = """
                syntax = "proto3";
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertSame(ProtobufVersion.PROTOBUF_3, document.syntax().orElse(null));
    }

    @Test
    public void testMissingSyntaxDeclaration() {
        var proto = """
                message M {}
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertTrue(document.syntax().isEmpty());
    }

    @Test
    public void testIncorrectPlacementOfSyntaxDeclaration() {
        var proto = """
                package foo;
                syntax = "proto3";
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));

        var proto1 = """
                import "other.proto";
                syntax = "proto3";
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto1, new ProtobufDocumentTree(Path.of("other.proto"))));
    }

    @Test
    public void testInvalidSyntaxString() {
        var proto1 = """
                syntax = "proto1";
                """;
        var proto4 = """
                syntax = "proto4";
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto1));
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto4));
    }

    @Test
    public void testValidPackageDeclaration() {
        var proto = """
                package foo.bar;
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertEquals("foo.bar", document.packageName().orElse(null));
    }

    @Test
    public void testMultiplePackageDeclarationsError() {
        var proto = """
                package foo;
                package bar;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackageNameValidity() {
        var valid = """
                package my_pkg.v1;
                """;
        var invalid = """
                package 1pkg.name;
                """;
        ProtobufParser.parseOnly(valid);
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(invalid));
    }

    @Test
    public void testBasicImportStatement() {
        var proto = """
                import "other.proto";
                """;
        var other = new ProtobufDocumentTree(Path.of("other.proto"));
        var document = ProtobufParser.parseOnly(proto, other);
        var importStatement = document.getDirectChildByType(ProtobufImportStatement.class).orElseThrow();
        assertSame(ProtobufImportStatement.Modifier.NONE, importStatement.modifier());
        assertEquals("other.proto", importStatement.location());
        assertSame(other, importStatement.document());
    }

    @Test
    public void testPublicImportStatement() {
        var proto = """
                import public "other.proto";
                """;
        var other = new ProtobufDocumentTree(Path.of("other.proto"));
        var document = ProtobufParser.parseOnly(proto, other);
        var importStatement = document.getDirectChildByType(ProtobufImportStatement.class).orElseThrow();
        assertSame(ProtobufImportStatement.Modifier.PUBLIC, importStatement.modifier());
        assertEquals("other.proto", importStatement.location());
        assertSame(other, importStatement.document());
    }

    @Test
    public void testWeakImportStatement() {
        var proto = """
                import weak "other.proto";
                """;
        var other = new ProtobufDocumentTree(Path.of("other.proto"));
        var document = ProtobufParser.parseOnly(proto, other);
        var importStatement = document.getDirectChildByType(ProtobufImportStatement.class).orElseThrow();
        assertSame(ProtobufImportStatement.Modifier.WEAK, importStatement.modifier());
        assertEquals("other.proto", importStatement.location());
        assertSame(other, importStatement.document());
    }

    @Test
    public void testInvalidImportModifier() {
        var proto = """
                import weaker "other.proto";
                """;
        var other = new ProtobufDocumentTree(Path.of("other.proto"));
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto, other));
    }

    @Test
    public void testInvalidImportPaths() {
        var proto = """
                import "missing;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testUnknownImportPaths() {
        var proto = """
                import "other.proto";
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testStandardLiteralFileLevelOption() {
        var proto = """
                option java_package = "com.example.foo";
                """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("java_package", option.name().toString());
        assertTrue(option.value() instanceof ProtobufLiteralExpression);
        assertEquals("com.example.foo", ((ProtobufLiteralExpression) option.value()).value());
    }

    @Test
    public void testStandardBoolTrueFileLevelOption() {
        var proto = """
                option java_multiple_files = true;
                """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("java_multiple_files", option.name().toString());
        assertTrue(option.value() instanceof ProtobufBoolExpression);
        assertSame(true, ((ProtobufBoolExpression) option.value()).value());
    }

    @Test
    public void testStandardBoolFalseFileLevelOption() {
        var proto = """
                option java_multiple_files = false;
                """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("java_multiple_files", option.name().toString());
        assertTrue(option.value() instanceof ProtobufBoolExpression);
        assertSame(false, ((ProtobufBoolExpression) option.value()).value());
    }

    @Test
    public void testStandardEnumConstantFileLevelOption() {
        var proto = """
                option optimize_for = SPEED;
                """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("optimize_for", option.name().toString());
        assertTrue(option.value() instanceof ProtobufEnumConstantExpression);
        assertEquals("SPEED", ((ProtobufEnumConstantExpression) option.value()).name());
    }

    // There are no standard int, float or message file level options to test

    @Test
    public void testStandardFileLevelOptionWithParenthesisError() {
        var proto = """
                option (optimize_for) = SPEED;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testCustomMessageFileLevelOption() {
        var proto = """
                message MyOption {
                  optional bool a = 1;
                }
                
                extend google.protobuf.FileOptions {
                  optional bool simple_option = 1000;
                  optional MyOption structured_option = 1001;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var proto1 = """
                option (simple_option) = true;
                option (structured_option).a = true;
                """;
        ProtobufParser.parseOnly(proto1, document);
    }

    @Test
    public void testInvalidFileLevelOptionNamesOrValues() {
        var proto = """
                option abc = 123;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));

        var proto1 = """
                option java_package = 123;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto1));
    }

    @Test
    public void testEmptyMessageDefinition() {
        var proto = """
                message MyMessage {}
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        assertEquals("MyMessage", message.name());
    }

    @Test
    public void testProto2MessageWithBasicScalarFields() {
        var proto = """
                message M {
                  optional int32 id = 1;
                  optional string name = 2;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class)
                .orElseThrow();
        assertEquals("M", message.name());
        assertSame(2, message.children().size());
        var firstField = message.getDirectChildByIndexAndType(1, ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufFieldStatement.Modifier.OPTIONAL, firstField.modifier());
        assertTrue(firstField.type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.INT32, firstField.type().protobufType());
        assertEquals("id", firstField.name());
        assertEquals((Long) 1L, firstField.index());
        var secondField = message.getDirectChildByIndexAndType(2, ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufFieldStatement.Modifier.OPTIONAL, secondField.modifier());
        assertTrue(secondField.type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.STRING, secondField.type().protobufType());
        assertEquals("name", secondField.name());
        assertEquals((Long) 2L, secondField.index());
    }

    @Test
    public void testProto3MessageWithBasicScalarFields() {
        var proto = """
                syntax = "proto3";
                
                message M {
                  int32 id = 1;
                  string name = 2;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        assertEquals("M", message.name());
        assertSame(2, message.children().size());
        var firstField = message.getDirectChildByIndexAndType(1, ProtobufFieldStatement.class).orElseThrow();
        assertEquals(ProtobufFieldStatement.Modifier.NONE, firstField.modifier());
        assertTrue(firstField.type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.INT32, firstField.type().protobufType());
        assertEquals("id", firstField.name());
        assertEquals((Long) 1L, firstField.index());
        var secondField = message.getDirectChildByIndexAndType(2, ProtobufFieldStatement.class).orElseThrow();
        assertEquals(ProtobufFieldStatement.Modifier.NONE, secondField.modifier());
        assertTrue(secondField.type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.STRING, secondField.type().protobufType());
        assertEquals("name", secondField.name());
        assertEquals((Long) 2L, secondField.index());
    }

    @Test
    public void testMessageWithNestedMessages() {
        var proto = """
                message M {
                  message N {}
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("M", ProtobufMessageStatement.class).orElseThrow();
        assertSame(1, message.children().size());
        var nestedMessage = message.getDirectChildByNameAndType("N", ProtobufMessageStatement.class).orElseThrow();
        assertSame(0, nestedMessage.children().size());
    }

    @Test
    public void testMessageWithEnums() {
        var proto = """
                message M {
                  enum E { A = 0; }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("M", ProtobufMessageStatement.class).orElseThrow();
        assertSame(1, message.children().size());
        var nestedEnum = message.getDirectChildByNameAndType("E", ProtobufEnumStatement.class).orElseThrow();
        assertSame(1, nestedEnum.children().size());
        var enumConstant = nestedEnum.getDirectChildByIndexAndType(0, ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals("A", enumConstant.name());
    }

    @Test
    public void testMessageWithOneofFields() {
        var proto = """
                message M {
                  oneof my_union {
                    string name = 1;
                    int32 id = 2;
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        assertEquals("M", message.name());
        var oneof = message.getDirectChildByType(ProtobufOneofFieldStatement.class).orElseThrow();
        assertEquals("my_union", oneof.name());
        assertEquals(2, oneof.children().size());
        var nameField = oneof.getDirectChildByNameAndType("name", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(nameField.type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.STRING, nameField.type().protobufType());
        assertEquals(Long.valueOf(1), nameField.index());
        var idField = oneof.getDirectChildByNameAndType("id", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(idField.type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.INT32, idField.type().protobufType());
        assertEquals(Long.valueOf(2), idField.index());
    }

    @Test
    public void testMessageWithGroups() {
        var proto = """
                message M {
                  repeated group Result = 2 {
                    optional string name = 3;
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var group = message.getDirectChildByType(ProtobufGroupFieldStatement.class).orElseThrow();
        assertEquals("Result", group.name());
        assertEquals(Long.valueOf(2), group.index());
        assertEquals(1, group.children().size());
        var innerField = group.getDirectChildByNameAndType("name", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufType.STRING, innerField.type().protobufType());
        assertEquals(Long.valueOf(3), innerField.index());
    }

    @Test
    public void testMessageWithMapFields() {
        var proto = """
                message M {
                  map<string, int32> map_field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByNameAndType("map_field", ProtobufFieldStatement.class).orElseThrow();
        assertTrue(field.type() instanceof ProtobufMapTypeReference);
        var mapType = (ProtobufMapTypeReference) field.type();
        assertSame(ProtobufType.STRING, mapType.keyType().protobufType());
        assertSame(ProtobufType.INT32, mapType.valueType().protobufType());
        assertEquals(Long.valueOf(1), field.index());
    }

    @Test
    public void testMessageWithMapFieldsAndModifierError() {
        var proto = """
                message M {
                  required map<string, int32> map_field = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnums() {
        var proto = """
                enum E {
                  UNKNOWN = 0;
                  STARTED = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var enumStmt = document.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        assertEquals("E", enumStmt.name());
        assertEquals(2, enumStmt.children().size());
        var unknown = enumStmt.getDirectChildByNameAndType("UNKNOWN", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(Long.valueOf(0), unknown.index());
        var started = enumStmt.getDirectChildByNameAndType("STARTED", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(Long.valueOf(1), started.index());
    }

    @Test
    public void testEnumWithOptions() {
        var proto = """
                enum E {
                  option deprecated = true;
                  A = 0;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var enumStmt = document.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        var opt = enumStmt.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertTrue(opt.value() instanceof ProtobufBoolExpression);
        assertSame(true, ((ProtobufBoolExpression) opt.value()).value());
        var constant = enumStmt.getDirectChildByNameAndType("A", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(Long.valueOf(0), constant.index());
    }

    @Test
    public void testService() {
        var proto = """
                service S {
                  rpc Fetch (Request) returns (Response);
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var service = document.getDirectChildByType(ProtobufServiceStatement.class).orElseThrow();
        assertEquals("S", service.name());
        var method = service.getDirectChildByType(ProtobufMethodStatement.class).orElseThrow();
        assertEquals("Fetch", method.name());
        assertEquals("Request", method.inputType().value().name());
        assertFalse(method.inputType().stream());
        assertEquals("Response", method.outputType().value().name());
        assertFalse(method.outputType().stream());
    }

    @Test
    public void testServiceWithStreamResponse() {
        var proto = """
                service S {
                  rpc Fetch (Request) returns (stream Response);
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var service = document.getDirectChildByType(ProtobufServiceStatement.class).orElseThrow();
        assertEquals("S", service.name());
        var method = service.getDirectChildByType(ProtobufMethodStatement.class).orElseThrow();
        assertEquals("Fetch", method.name());
        assertEquals("Request", method.inputType().value().name());
        assertFalse(method.inputType().stream());
        assertEquals("Response", method.outputType().value().name());
        assertTrue(method.outputType().stream());
    }

    @Test
    public void testServiceWithStreamRequest() {
        var proto = """
                service S {
                  rpc Fetch (stream Request) returns (Response);
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var service = document.getDirectChildByType(ProtobufServiceStatement.class).orElseThrow();
        assertEquals("S", service.name());
        var method = service.getDirectChildByType(ProtobufMethodStatement.class).orElseThrow();
        assertEquals("Fetch", method.name());
        assertEquals("Request", method.inputType().value().name());
        assertTrue(method.inputType().stream());
        assertEquals("Response", method.outputType().value().name());
        assertFalse(method.outputType().stream());
    }

    @Test
    public void testExtendDefinition() {
        var proto = """
                message MessageType {
                
                }
                
                extend MessageType {
                  optional string ext_field = 100;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var extend = document.getDirectChildrenByType(ProtobufExtendStatement.class)
                .findFirst()
                .orElseThrow();
        assertEquals("MessageType", extend.declaration().name());
        var field = extend.getDirectChildByNameAndType("ext_field", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufType.STRING, field.type().protobufType());
        assertEquals(Long.valueOf(100), field.index());
    }

    @Test
    public void testExtendDefinitionOnUnknownError() {
        var proto = """
                extend MessageType {
                  optional string ext_field = 100;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMissingSemicolons() {
        var proto = """
                syntax = "proto3"
                message A {
                  int32 id = 1
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMismatchedBraces() {
        var proto = """
                syntax = "proto3";
                message A {
                  message B {
                  }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMismatchedParentheses() {
        // Missing closing ")" after Req
        var proto = """
                syntax = "proto3";
                service S {
                  rpc M (Req returns (Res)) {}
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMismatchedQuotes() {
        var proto = """
                syntax = "proto3;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testInvalidKeywordsAsIdentifiers() {
        // Using the keyword "message" as a field name should fail
        var proto = """
                syntax = "proto3";
                message Demo {
                  int32 message = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testInvalidIdentifiersInPlaceOfKeywords() {
        // Top-level "massage" instead of "message" should fail
        var proto = """
                syntax = "proto3";
                massage Demo {
                  int32 id = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testIncorrectFieldSyntaxMissingFieldNumber() {
        var proto = """
                syntax = "proto3";
                message A {
                  int32 id;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testUnexpectedTokens() {
        var proto = """
                syntax = "proto3";
                # this is not a valid comment in proto
                message A { int32 id = 1; }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    // 5.2 Semantic Errors

    @Test
    public void testDuplicateFieldNumbersError() {
        var proto = """
                syntax = "proto3";
                message A {
                  int32 a = 1;
                  string b = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDuplicateFieldNamesError() {
        var proto = """
                syntax = "proto3";
                message A {
                  int32 x = 1;
                  string x = 2;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedNumberUsageError() {
        var proto = """
                syntax = "proto3";
                message A {
                  reserved 5;
                  int32 a = 5;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedNameUsageError() {
        var proto = """
                syntax = "proto3";
                message A {
                  reserved "foo";
                  int32 foo = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testTypeMismatchesFieldDefaultValue() {
        // In proto2, default is allowed but must match the field type
        var proto = """
                syntax = "proto2";
                message A {
                  int32 a = 1 [default = "abc"];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testTypeMismatchesInvalidMapKeyType() {
        // double is not a valid map key type
        var proto = """
                syntax = "proto3";
                message A {
                  map<double, string> m = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testProto2FeaturesInProto3ErrorRequiredFields() {
        var proto = """
                syntax = "proto3";
                message A {
                  required int32 a = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testProto2FeaturesInProto3ErrorGroupFields() {
        var proto = """
                syntax = "proto3";
                message A {
                  group Foo = 1 {}
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testProto2FeaturesInProto3ErrorExtensions() {
        var proto = """
                syntax = "proto3";
                message A {
                  extensions 100 to 199;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testInvalidOneofDefinitionRepeatedField() {
        var proto = """
                syntax = "proto3";
                message A {
                  oneof x {
                    repeated int32 a = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testInvalidOneofDefinitionMapField() {
        var proto = """
                syntax = "proto3";
                message A {
                  oneof x {
                    map<int32, string> m = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumZeroValueViolationError() {
        // In proto3, the first enum value must be zero
        var proto = """
                syntax = "proto3";
                enum E {
                  ONE = 1;
                  ZERO = 0;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumAliasWithoutAllowAliasWarningOrError() {
        var proto = """
                syntax = "proto3";
                enum E {
                  A = 1;
                  B = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Ignore("Requires multi-file resolution/import graph handling")
    @Test
    public void testCircularDependenciesWarning() {
        // Intentionally left ignored: would require an import resolver/environment
    }

    // 5.3 Malformed Input

    @Test
    public void testEmptyFile() {
        var proto = "";
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testFileWithOnlyCommentsAndWhitespace() {
        var proto = """
                
                // single-line comment
                /* block
                   comment */
                
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testBinaryDataAsInputError() {
        // Embed control chars that should not be acceptable in proto source
        var proto = "syntax = \"proto3\";\u0000\u0001message A { int32 id = 1; }";
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    // 5.4 Large and Complex Files

    @Test(timeout = 8000)
    public void testExtremelyLargeFilePerformance() {
        var sb = new StringBuilder();
        sb.append("syntax = \"proto3\";\n");
        sb.append("message Big {\n");
        for (var i = 1; i <= 2000; i++) {
            sb.append("  int32 f").append(i).append(" = ").append(i).append(";\n");
        }
        sb.append("}\n");
        ProtobufParser.parseOnly(sb.toString());
    }

    @Test
    public void testManyTopLevelMessagesEnumsServices() {
        var sb = new StringBuilder();
        sb.append("syntax = \"proto3\";\n");

        for (var i = 1; i <= 50; i++) {
            sb.append("message M").append(i).append(" { int32 id = 1; }\n");
        }
        for (var i = 1; i <= 20; i++) {
            sb.append("enum E").append(i).append(" { ZERO = 0; ONE = 1; }\n");
        }
        sb.append("service S {\n");
        for (var i = 1; i <= 25; i++) {
            sb.append("  rpc R").append(i).append(" (Req) returns (Res) {}\n");
        }
        sb.append("}\n");

        // Add basic message types used by the service
        sb.append("message Req { int32 x = 1; }\n");
        sb.append("message Res { int32 y = 1; }\n");

        ProtobufParser.parseOnly(sb.toString());
    }

    @Test
    public void testDeeplyNestedStructures() {
        var sb = new StringBuilder();
        sb.append("syntax = \"proto3\";\n");
        var depth = 20;
        for (var i = 1; i <= depth; i++) {
            sb.append("message N").append(i).append(" {\n");
        }
        sb.append("  int32 leaf = 1;\n");
        for (var i = 1; i <= depth; i++) {
            sb.append("}\n");
        }
        ProtobufParser.parseOnly(sb.toString());
    }

    @Test
    public void testExtensiveUseOfOptions() {
        var proto = """
                syntax = "proto3";
                option java_package = "com.example";
                option java_outer_classname = "Outer";
                
                message A {
                  option (my_custom_message_option) = true;
                  int32 id = 1 [deprecated = true];
                }
                
                enum E {
                  option (my_custom_enum_option) = "x";
                  ZERO = 0 [(my_custom_enum_value_option) = 123];
                  ONE = 1;
                }
                
                service S {
                  option (my_custom_service_option) = "svc";
                  rpc Do (Req) returns (Res) {
                    option (my_custom_rpc_option) = "rpc";
                  }
                }
                
                message Req { int32 x = 1; }
                message Res { int32 y = 1; }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Ignore("Requires multi-file parsing and import resolution")
    @Test
    public void testInterdependentFiles() {
        // Intentionally left ignored: would require multi-file import resolution
    }

    @Ignore("Requires multi-file parsing and import resolution")
    @Test
    public void testMixedProto2AndProto3Imports() {
        // Intentionally left ignored: would require multi-file import resolution
    }

    // 3.1 Field Definitions
    // 3.1.1 Scalar Types
    @Test
    public void testAllScalarTypesRecognition() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message AllScalars {
                  double  a = 1;
                  float   b = 2;
                  int32   c = 3;
                  int64   d = 4;
                  uint32  e = 5;
                  uint64  f = 6;
                  sint32  g = 7;
                  sint64  h = 8;
                  fixed32 i = 9;
                  fixed64 j = 10;
                  sfixed32 k = 11;
                  sfixed64 l = 12;
                  bool    m = 13;
                  string  n = 14;
                  bytes   o = 15;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testSint32AndSint64EfficiencyNote() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  sint32 s32 = 1;
                  sint64 s64 = 2;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testStringFieldsUTF8EncodingNote() {
        // Parser should accept UTF-8 string literals in proto2 defaults
        var proto = """
                syntax = "proto2";
                package test;
                
                message M {
                  optional string greeting = 1 [default = "こんにちは"];
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testBytesFieldsArbitrarySequenceNote() {
        // Parser should accept hex escapes in bytes defaults
        var proto = """
                syntax = "proto2";
                package test;
                
                message M {
                  optional bytes payload = 1 [default = "\\x00\\xFF\\x10"];
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testScalarTypeInternalRepresentationAlignment() {
        // Sanity: ensure all scalar aliases are accepted as-is
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  sfixed32 a = 1;
                  sfixed64 b = 2;
                  fixed32  c = 3;
                  fixed64  d = 4;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    // 3.1.2 Cardinality (Required, Optional, Repeated, Implicit)
    @Test
    public void testProto2RequiredFields() {
        var proto = """
                syntax = "proto2";
                package test;
                
                message M {
                  required string name = 1;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testProto2OptionalFields() {
        var proto = """
                syntax = "proto2";
                package test;
                
                message M {
                  optional int32 id = 1;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testProto3ExplicitOptionalFields() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  optional string nickname = 1;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testProto3ImplicitPresenceFields() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  string title = 1;
                  int32  count = 2;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testRepeatedFields() {
        var proto = """
                syntax = "proto3";
                package test;
                
                enum E { UNKNOWN = 0; A = 1; }
                message M {
                  repeated int32 values = 1;
                  repeated E enums = 2;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testRepeatedScalarNumericTypesPackedByDefaultProto3() {
        // Ensure parser accepts repeated numeric with and without explicit packed option
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  repeated int32 nums = 1;
                  repeated int32 nums2 = 2 [packed = true];
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testMapFields() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  map<int32, string> dict = 1;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testMapFieldsCannotHaveOtherCardinalityLabels() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  repeated map<int32, string> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testInvalidCardinalityCombinations() {
        var proto = """
                syntax = "proto2";
                package test;
                
                message M {
                  required optional int32 invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    // 3.1.3 Field Numbers
    @Test
    public void testValidFieldNumbers() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  string a = 1;
                  int32  b = 12345;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testReservedFieldNumbersError() {
        // 19000–19999 are reserved for internal use
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  string internal = 19000;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOutOfRangeFieldNumbers() {
        var protoZero = """
                syntax = "proto3";
                package test;
                
                message M {
                  string a = 0;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoZero));

        var protoTooHigh = """
                syntax = "proto3";
                package test;
                
                message M {
                  string a = 536870912; // 2^29 exceeds max valid field number
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoTooHigh));
    }

    @Test
    public void testFieldNumbersWithinUserDefinedReservedBlocksError() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  reserved 10 to 20, 30;
                  string inBlock = 15;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    // 3.1.4 Default Values (Proto2 Specific)
    @Test
    public void testValidDefaultValuesProto2() {
        var proto = """
                syntax = "proto2";
                package test;
                
                enum E { UNKNOWN = 0; A = 1; }
                message M {
                  optional int32   i = 1 [default = 42];
                  optional bool    b = 2 [default = true];
                  optional string  s = 3 [default = "ok"];
                  optional bytes   by = 4 [default = "\\x01\\x02"];
                  optional E       e = 5 [default = A];
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testTypeMismatchForDefaultValuesErrorProto2() {
        var proto = """
                syntax = "proto2";
                package test;
                
                message M {
                  optional int32 i = 1 [default = "oops"];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDisallowedDefaultValuesProto2() {
        // Defaults are not allowed on repeated fields or message-typed fields
        var protoRepeated = """
                syntax = "proto2";
                package test;
                
                message M {
                  repeated int32 values = 1 [default = 1];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoRepeated));

        var protoMessage = """
                syntax = "proto2";
                package test;
                
                message Sub {}
                message M {
                  optional Sub sub = 1 [default = {}];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoMessage));
    }

    @Test
    public void testDefaultValuesInProto3Error() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  int32 i = 1 [default = 5];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    // 3.1.5 Field Options (e.g., packed, deprecated, custom options)
    @Test
    public void testPackedOption() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  repeated int32 nums = 1 [packed = true];
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testPackedOptionAllowedOnlyOnRepeatedScalarNumericTypes() {
        var protoOnString = """
                syntax = "proto3";
                package test;
                
                message M {
                  repeated string names = 1 [packed = true];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoOnString));

        var protoNotRepeated = """
                syntax = "proto3";
                package test;
                
                message M {
                  int32 value = 1 [packed = true];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoNotRepeated));

        var protoOnBytes = """
                syntax = "proto3";
                package test;
                
                message M {
                  repeated bytes data = 1 [packed = true];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoOnBytes));
    }

    @Test
    public void testDeprecatedOption() {
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  int32 id = 1 [deprecated = true];
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testCustomFieldOptions() {
        // Use an extension-like option name; parser should accept syntactically even if unknown
        var proto = """
                syntax = "proto3";
                package test;
                
                message M {
                  int32 id = 1 [(test.my_field_option) = true];
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testInvalidOptionSyntaxOrValues() {
        var protoMissingEquals = """
                syntax = "proto3";
                package test;
                
                message M {
                  int32 id = 1 [deprecated];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoMissingEquals));
    }
}
