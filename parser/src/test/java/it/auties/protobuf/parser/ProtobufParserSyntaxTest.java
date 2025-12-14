package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.expression.ProtobufBoolExpression;
import it.auties.protobuf.parser.expression.ProtobufEnumConstantExpression;
import it.auties.protobuf.parser.expression.ProtobufLiteralExpression;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.typeReference.ProtobufMapTypeReference;
import it.auties.protobuf.parser.typeReference.ProtobufPrimitiveTypeReference;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

class ProtobufParserSyntaxTest {
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
        assertInstanceOf(ProtobufLiteralExpression.class, option.value());
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
        assertInstanceOf(ProtobufBoolExpression.class, option.value());
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
        assertInstanceOf(ProtobufBoolExpression.class, option.value());
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
        assertInstanceOf(ProtobufEnumConstantExpression.class, option.value());
        assertEquals("SPEED", ((ProtobufEnumConstantExpression) option.value()).name());
    }

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
               import "google/protobuf/descriptor.proto";
                
                message MyOption {
                  optional bool a = 1;
                }
                
                extend google.protobuf.FileOptions {
                  optional bool simple_option = 1000;
                  optional MyOption structured_option = 1001;
                }
                
                option (simple_option) = true;
                option (structured_option).a = true;
                """;
        ProtobufParser.parseOnly(proto);
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
        assertSame(ProtobufModifier.OPTIONAL, firstField.modifier());
        assertInstanceOf(ProtobufPrimitiveTypeReference.class, firstField.type());
        assertSame(ProtobufType.INT32, firstField.type().protobufType());
        assertEquals("id", firstField.name());
        assertEquals(1L, firstField.index().value().longValue());
        var secondField = message.getDirectChildByIndexAndType(2, ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufModifier.OPTIONAL, secondField.modifier());
        assertInstanceOf(ProtobufPrimitiveTypeReference.class, secondField.type());
        assertSame(ProtobufType.STRING, secondField.type().protobufType());
        assertEquals("name", secondField.name());
        assertEquals(2L, secondField.index().value().longValue());
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
        assertEquals(ProtobufModifier.NONE, firstField.modifier());
        assertInstanceOf(ProtobufPrimitiveTypeReference.class, firstField.type());
        assertSame(ProtobufType.INT32, firstField.type().protobufType());
        assertEquals("id", firstField.name());
        assertEquals(1L, firstField.index().value().longValue());
        var secondField = message.getDirectChildByIndexAndType(2, ProtobufFieldStatement.class).orElseThrow();
        assertEquals(ProtobufModifier.NONE, secondField.modifier());
        assertInstanceOf(ProtobufPrimitiveTypeReference.class, secondField.type());
        assertSame(ProtobufType.STRING, secondField.type().protobufType());
        assertEquals("name", secondField.name());
        assertEquals(2L, secondField.index().value().longValue());
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
        var oneof = message.getDirectChildByType(ProtobufOneofStatement.class).orElseThrow();
        assertEquals("my_union", oneof.name());
        assertEquals(2, oneof.children().size());
        var nameField = oneof.getDirectChildByNameAndType("name", ProtobufFieldStatement.class).orElseThrow();
        assertInstanceOf(ProtobufPrimitiveTypeReference.class, nameField.type());
        assertSame(ProtobufType.STRING, nameField.type().protobufType());
        assertEquals(1L, nameField.index().value().longValue());
        var idField = oneof.getDirectChildByNameAndType("id", ProtobufFieldStatement.class).orElseThrow();
        assertInstanceOf(ProtobufPrimitiveTypeReference.class, idField.type());
        assertSame(ProtobufType.INT32, idField.type().protobufType());
        assertEquals(2L, idField.index().value().longValue());
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
        var group = message.getDirectChildByType(ProtobufGroupStatement.class).orElseThrow();
        assertEquals("Result", group.name());
        assertEquals(2L, group.index().value().longValue());
        assertEquals(1, group.children().size());
        var innerField = group.getDirectChildByNameAndType("name", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufType.STRING, innerField.type().protobufType());
        assertEquals(3L, innerField.index().value().longValue());
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
        assertInstanceOf(ProtobufMapTypeReference.class, field.type());
        var mapType = (ProtobufMapTypeReference) field.type();
        assertSame(ProtobufType.STRING, mapType.keyType().protobufType());
        assertSame(ProtobufType.INT32, mapType.valueType().protobufType());
        assertEquals(1L, field.index().value().longValue());
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
        assertEquals(0L, unknown.index().value().longValue());
        var started = enumStmt.getDirectChildByNameAndType("STARTED", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(1L, started.index().value().longValue());
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
        assertInstanceOf(ProtobufBoolExpression.class, opt.value());
        assertSame(true, ((ProtobufBoolExpression) opt.value()).value());
        var constant = enumStmt.getDirectChildByNameAndType("A", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(0L, constant.index().value().longValue());
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
                  extensions 100;
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
        assertEquals(100L, field.index().value().longValue());
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
    public void testInvalidIdentifiersInPlaceOfKeywords() {
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
        sb.append("}\n".repeat(depth));
        ProtobufParser.parseOnly(sb.toString());
    }

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
                  string a = 536870912;
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

    @Test
    public void testPackageSingleComponent() {
        var proto = """
                package mypackage;
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertEquals("mypackage", document.packageName().orElse(null));
    }

    @Test
    public void testPackageVeryLongName() {
        var proto = """
                package com.company.division.team.project.module.submodule.component.feature.impl;
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertEquals("com.company.division.team.project.module.submodule.component.feature.impl",
                document.packageName().orElse(null));
    }

    @Test
    public void testPackageWithUnderscores() {
        var proto = """
                package my_package.sub_package;
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertEquals("my_package.sub_package", document.packageName().orElse(null));
    }

    @Test
    public void testPackageWithNumbers() {
        var proto = """
                package pkg.v1.v2;
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertEquals("pkg.v1.v2", document.packageName().orElse(null));
    }

    @Test
    public void testPackageStartingWithDotError() {
        var proto = """
                package .invalid;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackageEndingWithDotError() {
        var proto = """
                package invalid.;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackageWithDoubleDotError() {
        var proto = """
                package invalid..package;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMultipleImports() throws IOException {
        var proto = """
                import "file1.proto";
                import "file2.proto";
                import "file3.proto";
                """;
        var tempDir = Files.createTempDirectory("protobuf-test");
        var file1Path = tempDir.resolve("file1.proto");
        var file2Path = tempDir.resolve("file2.proto");
        var file3Path = tempDir.resolve("file3.proto");
        Files.writeString(file1Path, "syntax = \"proto3\";", StandardOpenOption.CREATE);
        Files.writeString(file2Path, "syntax = \"proto3\";", StandardOpenOption.CREATE);
        Files.writeString(file3Path, "syntax = \"proto3\";", StandardOpenOption.CREATE);

        var file1 = ProtobufParser.parseOnly(file1Path);
        var file2 = ProtobufParser.parseOnly(file2Path);
        var file3 = ProtobufParser.parseOnly(file3Path);
        var document = ProtobufParser.parseOnly(proto, file1, file2, file3);
        assertEquals(3, document.getDirectChildrenByType(ProtobufImportStatement.class).count());
    }

    @Test
    public void testServiceEmpty() {
        var proto = """
                service EmptyService {
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var service = document.getDirectChildByType(ProtobufServiceStatement.class).orElseThrow();
        assertEquals("EmptyService", service.name());
        assertEquals(0, service.children().size());
    }

    @Test
    public void testServiceWithMultipleMethods() {
        var proto = """
                message Req { int32 x = 1; }
                message Res { int32 y = 1; }
                
                service MyService {
                  rpc Method1 (Req) returns (Res);
                  rpc Method2 (Req) returns (Res);
                  rpc Method3 (Req) returns (Res);
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var service = document.getDirectChildByNameAndType("MyService", ProtobufServiceStatement.class).orElseThrow();
        assertEquals(3, service.getDirectChildrenByType(ProtobufMethodStatement.class).count());
    }

    @Test
    public void testServiceWithBidirectionalStream() {
        var proto = """
                message Req { int32 x = 1; }
                message Res { int32 y = 1; }
                
                service MyService {
                  rpc BiStream (stream Req) returns (stream Res);
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var service = document.getDirectChildByType(ProtobufServiceStatement.class).orElseThrow();
        var method = service.getDirectChildByType(ProtobufMethodStatement.class).orElseThrow();
        assertTrue(method.inputType().stream());
        assertTrue(method.outputType().stream());
    }

    @Test
    public void testServiceMethodWithOptions() {
        var proto = """
                message Req { int32 x = 1; }
                message Res { int32 y = 1; }
                
                service MyService {
                  rpc MyMethod (Req) returns (Res) {
                    option deprecated = true;
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var service = document.getDirectChildByType(ProtobufServiceStatement.class).orElseThrow();
        var method = service.getDirectChildByType(ProtobufMethodStatement.class).orElseThrow();
        assertNotNull(method);
    }

    @Test
    public void testServiceWithOptions() {
        var proto = """
                service MyService {
                  option deprecated = true;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var service = document.getDirectChildByType(ProtobufServiceStatement.class).orElseThrow();
        var option = service.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("deprecated", option.name().toString());
    }

    @Test
    public void testDeeplyNestedMessages() {
        var proto = """
                message L1 {
                  message L2 {
                    message L3 {
                      message L4 {
                        message L5 {
                          int32 value = 1;
                        }
                      }
                    }
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testNestedMessageWithSameNameAsParent() {
        var proto = """
                message Message {
                  message Message {
                    int32 value = 1;
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testNestedMessageReferencingParentType() {
        var proto = """
                message Outer {
                  int32 id = 1;
                
                  message Inner {
                    Outer parent = 1;
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testNestedMessageCircularReference() {
        var proto = """
                message Node {
                  int32 value = 1;
                  Node next = 2;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testEnumWithManyValues() {
        var proto = """
                enum Status {
                  UNKNOWN = 0;
                  VALUE1 = 1;
                  VALUE2 = 2;
                  VALUE3 = 3;
                  VALUE4 = 4;
                  VALUE5 = 5;
                  VALUE6 = 6;
                  VALUE7 = 7;
                  VALUE8 = 8;
                  VALUE9 = 9;
                  VALUE10 = 10;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var enumStmt = document.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        assertEquals(11, enumStmt.getDirectChildrenByType(ProtobufEnumConstantStatement.class).count());
    }

    @Test
    public void testEnumWithNonSequentialValues() {
        var proto = """
                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 5;
                  INACTIVE = 10;
                  DELETED = 100;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testEnumValueWithOptions() {
        var proto = """
                enum Status {
                  UNKNOWN = 0;
                  DEPRECATED_VALUE = 1 [deprecated = true];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var enumStmt = document.getDirectChildByType(ProtobufEnumStatement.class).orElseThrow();
        var constant = enumStmt.getDirectChildByNameAndType("DEPRECATED_VALUE", ProtobufEnumConstantStatement.class).orElseThrow();
        assertTrue(constant.options().stream()
                .anyMatch(opt -> opt.name().toString().equals("deprecated")));
    }

    @Test
    public void testMultipleOptionsInSameBracket() {
        var proto = """
                syntax = "proto3";
                
                message M {
                  repeated int32 field = 1 [deprecated = true, packed = false];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var field = message.getDirectChildByType(ProtobufFieldStatement.class).orElseThrow();
        assertEquals(2, field.options().size());
    }

    @Test
    public void testOptionWithDottedName() {
        var proto = """
                syntax = "proto3";
                
                message M {
                  int32 field = 1 [(custom.option) = true];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testOptionWithMessageValue() {
        var proto = """
                syntax = "proto3";
                
                message M {
                  int32 field = 1 [(custom.option).sub_field = "value"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testReservedMultipleStatements() {
        var proto = """
                syntax = "proto3";
                
                message M {
                  reserved 1, 2, 3;
                  reserved 10 to 20;
                  reserved "foo", "bar";
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        assertEquals(3, message.getDirectChildrenByType(ProtobufReservedStatement.class).count());
    }

    @Test
    public void testReservedSingleNumber() {
        var proto = """
                syntax = "proto3";
                
                message M {
                  reserved 5;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testReservedSingleName() {
        var proto = """
                syntax = "proto3";
                
                message M {
                  reserved "field_name";
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testGroupWithOptionalModifier() {
        var proto = """
                syntax = "proto2";
                
                message M {
                  optional group MyGroup = 1 {
                    optional string field = 2;
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var group = message.getDirectChildByType(ProtobufGroupStatement.class).orElseThrow();
        assertEquals("MyGroup", group.name());
    }

    @Test
    public void testGroupNameMustBeCapitalized() {
        var proto = """
                syntax = "proto2";
                
                message M {
                  repeated group Result = 1 {
                    optional string name = 2;
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testGroupWithMultipleFields() {
        var proto = """
                syntax = "proto2";
                
                message M {
                  repeated group Data = 1 {
                    optional string name = 2;
                    optional int32 value = 3;
                    optional bool flag = 4;
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var group = message.getDirectChildByType(ProtobufGroupStatement.class).orElseThrow();
        assertEquals(3, group.children().size());
    }

    @Test
    public void testExtensionRangeSingleNumber() {
        var proto = """
                syntax = "proto2";
                
                message M {
                  extensions 100;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testExtensionRangeMultiple() {
        var proto = """
                syntax = "proto2";
                
                message M {
                  extensions 100 to 199, 500, 1000 to max;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testCorrectFileOrdering() {
        var proto = """
                syntax = "proto3";
                package mypackage;
                import "other.proto";
                option java_package = "com.example";
                
                message M {
                  int32 field = 1;
                }
                """;
        var other = new ProtobufDocumentTree(Path.of("other.proto"));
        var document = ProtobufParser.parseOnly(proto, other);
        assertNotNull(document);
    }

    @Test
    public void testSyntaxMustBeFirst() {
        var proto = """
                package mypackage;
                syntax = "proto3";
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testRepeatedScalarField() {
        var proto = """
                syntax = "proto3";
                
                message M {
                  repeated int32 values = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testRepeatedMessageField() {
        var proto = """
                syntax = "proto3";
                
                message Sub {
                  int32 x = 1;
                }
                
                message M {
                  repeated Sub items = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testRepeatedEnumField() {
        var proto = """
                syntax = "proto3";

                enum E { UNKNOWN = 0; A = 1; }

                message M {
                  repeated E values = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    // Additional comprehensive tests for full spec compliance

    @Test
    public void testMultipleSyntaxDeclarationsError() {
        var proto = """
                syntax = "proto3";
                syntax = "proto2";
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNumberAtWireFormatBoundary15() {
        var proto = """
                syntax = "proto3";
                message M {
                  string field15 = 15;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFieldNumberAtWireFormatBoundary16() {
        var proto = """
                syntax = "proto3";
                message M {
                  string field16 = 16;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFieldNumberAtWireFormatBoundary2047() {
        var proto = """
                syntax = "proto3";
                message M {
                  string field2047 = 2047;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFieldNumberAtWireFormatBoundary2048() {
        var proto = """
                syntax = "proto3";
                message M {
                  string field2048 = 2048;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testJsonNameOption() {
        var proto = """
                syntax = "proto3";
                message M {
                  string my_field = 1 [json_name = "myField"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testPackedOptionExplicitlyFalse() {
        var proto = """
                syntax = "proto3";
                message M {
                  repeated int32 nums = 1 [packed = false];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testServiceMethodWithMultipleOptions() {
        var proto = """
                message Req { int32 x = 1; }
                message Res { int32 y = 1; }

                service S {
                  rpc M (Req) returns (Res) {
                    option deprecated = true;
                    option idempotency_level = NO_SIDE_EFFECTS;
                  }
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testExtensionRangeOverlappingError() {
        var proto = """
                syntax = "proto2";
                message M {
                  extensions 100 to 200;
                  extensions 150 to 250;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testGroupNameMustStartWithCapital() {
        var proto = """
                syntax = "proto2";
                message M {
                  repeated group lowercase = 1 {
                    optional string name = 2;
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
                    string a = 1;
                    oneof inner {
                      string b = 2;
                    }
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapAsExtensionFieldError() {
        var proto = """
                syntax = "proto2";
                message M {
                  extensions 100 to 200;
                }
                extend M {
                  map<string, int32> ext_map = 100;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testGroupInOneofError() {
        var proto = """
                syntax = "proto2";
                message M {
                  oneof choice {
                    group G = 1 {
                      optional string name = 2;
                    }
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDuplicateOneofNames() {
        var proto = """
                syntax = "proto3";
                message M {
                  oneof choice {
                    string a = 1;
                  }
                  oneof choice {
                    int32 b = 2;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldTypeReferenceAcrossFiles() throws IOException {
        var imported = """
                syntax = "proto3";
                package external;
                message ExternalType {
                  int32 value = 1;
                }
                """;
        var tempDir = Files.createTempDirectory("protobuf-test");
        var importedFile = tempDir.resolve("external.proto");
        Files.writeString(importedFile, imported, StandardOpenOption.CREATE);
        var importedDoc = ProtobufParser.parseOnly(importedFile);

        var main = """
                syntax = "proto3";
                import "external/external.proto";

                message MainType {
                  external.ExternalType field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(main, importedDoc);
        assertNotNull(document);
    }

    @Test
    public void testCircularMessageTypeReference() {
        var proto = """
                syntax = "proto3";
                message A {
                  B b = 1;
                }
                message B {
                  A a = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testSelfReferencingMessage() {
        var proto = """
                syntax = "proto3";
                message TreeNode {
                  int32 value = 1;
                  TreeNode left = 2;
                  TreeNode right = 3;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMultipleFieldOptionsOnSameField() {
        var proto = """
                syntax = "proto3";
                message M {
                  repeated int32 nums = 1 [packed = true, deprecated = true];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testOptionWithAggregateValue() {
        var proto = """
                syntax = "proto3";
                message M {
                  int32 field = 1 [(custom.option).sub_field = "value", (custom.option).num = 42];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testReservedRangeTouchingOtherRanges() {
        var proto = """
                syntax = "proto3";
                message M {
                  reserved 1 to 5;
                  reserved 6 to 10;
                  reserved 11 to 15;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testProto3OptionalFieldPresence() {
        var proto = """
                syntax = "proto3";
                message M {
                  optional string explicit = 1;
                  string implicit = 2;
                  optional int32 explicit_int = 3;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        var explicit = message.getDirectChildByNameAndType("explicit", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufModifier.OPTIONAL, explicit.modifier());
        var implicit = message.getDirectChildByNameAndType("implicit", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufModifier.NONE, implicit.modifier());
    }

    @Test
    public void testEmptyEnum() {
        var proto = """
                syntax = "proto3";
                enum E {
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEmptyService() {
        var proto = """
                syntax = "proto3";
                service S {
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testCommentBeforeSyntax() {
        var proto = """
                // File header comment
                /* Block comment */
                syntax = "proto3";
                message M {
                  string field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testReservedKeywordAsFieldName() {
        var proto = """
                syntax = "proto3";
                message M {
                  string option = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testPackageComponentStartingWithNumber() {
        var proto = """
                syntax = "proto3";
                package com.1example.foo;
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testValidPackageWithVersion() {
        var proto = """
                syntax = "proto3";
                package com.example.v1;
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertEquals("com.example.v1", document.packageName().orElse(null));
    }

    @Test
    public void testEnumValueMaxBoundary() {
        var proto = """
                syntax = "proto3";
                enum E {
                  ZERO = 0;
                  MAX_INT32 = 2147483647;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testEnumValueMinBoundary() {
        var proto = """
                syntax = "proto3";
                enum E {
                  ZERO = 0;
                  MIN_INT32 = -2147483648;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMapFieldCannotBeRepeated() {
        var proto = """
                syntax = "proto3";
                message M {
                  repeated map<string, int32> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapFieldCannotBeOptional() {
        var proto = """
                syntax = "proto3";
                message M {
                  optional map<string, int32> invalid = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionNumberAtMaxBoundary() {
        var proto = """
                syntax = "proto2";
                message M {
                  extensions 1000 to max;
                }
                extend M {
                  optional string field = 536870911;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFieldNumberSequentialFromOne() {
        var proto = """
                syntax = "proto3";
                message M {
                  string f1 = 1;
                  string f2 = 2;
                  string f3 = 3;
                  string f4 = 4;
                  string f5 = 5;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFieldNumberSparseAllocation() {
        var proto = """
                syntax = "proto3";
                message M {
                  string f1 = 1;
                  string f100 = 100;
                  string f1000 = 1000;
                  string f10000 = 10000;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testEnumWithSingleZeroValue() {
        var proto = """
                syntax = "proto3";
                enum E {
                  ZERO = 0;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testProto2EnumWithoutZeroValue() {
        var proto = """
                syntax = "proto2";
                enum E {
                  ONE = 1;
                  TWO = 2;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMessageWithOnlyReservedStatements() {
        var proto = """
                syntax = "proto3";
                message M {
                  reserved 1 to 10;
                  reserved "old_field1", "old_field2";
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMessageWithOnlyExtensionsDeclaration() {
        var proto = """
                syntax = "proto2";
                message M {
                  extensions 100 to max;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testComplexNestedStructure() {
        var proto = """
                syntax = "proto3";
                message Outer {
                  message Middle {
                    message Inner {
                      string value = 1;
                      enum Status {
                        UNKNOWN = 0;
                        ACTIVE = 1;
                      }
                      Status status = 2;
                    }
                    Inner inner = 1;
                  }
                  Middle middle = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testAllFieldModifierCombinationsProto2() {
        var proto = """
                syntax = "proto2";
                message M {
                  required string req = 1;
                  optional string opt = 2;
                  repeated string rep = 3;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testAllFieldModifierCombinationsProto3() {
        var proto = """
                syntax = "proto3";
                message M {
                  string implicit = 1;
                  optional string explicit = 2;
                  repeated string repeated_field = 3;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testPackedOptionOnRepeatedEnumField() {
        var proto = """
                syntax = "proto3";
                enum E { UNKNOWN = 0; A = 1; }
                message M {
                  repeated E enums = 1 [packed = true];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }
}
