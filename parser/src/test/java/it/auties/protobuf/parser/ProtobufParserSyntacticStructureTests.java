package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.ProtobufMapTypeReference;
import it.auties.protobuf.parser.type.ProtobufPrimitiveTypeReference;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.*;

public class ProtobufParserSyntacticStructureTests {
    @Test
    public void testValidProto2SyntaxDeclaration() {
        String proto = """
            syntax = "proto2";
            """;
        var document = ProtobufParser.parseOnly(proto);
        assertSame(ProtobufVersion.PROTOBUF_2, document.syntax().orElse(null));
    }

    @Test
    public void testValidProto3SyntaxDeclaration() {
        String proto = """
            syntax = "proto3";
            """;
        var document = ProtobufParser.parseOnly(proto);
        assertSame(ProtobufVersion.PROTOBUF_3, document.syntax().orElse(null));
    }

    @Test
    public void testMissingSyntaxDeclaration() {
        String proto = """
            message M {}
            """;
        var document = ProtobufParser.parseOnly(proto);
        assertTrue(document.syntax().isEmpty());
    }

    @Test
    public void testIncorrectPlacementOfSyntaxDeclaration() {
        String proto = """
            package foo;
            syntax = "proto3";
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));

        String proto1 = """
            import "other.proto";
            syntax = "proto3";
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto1, new ProtobufDocumentTree(Path.of("other.proto"))));
    }

    @Test
    public void testInvalidSyntaxString() {
        String proto1 = """
            syntax = "proto1";
            """;
        String proto4 = """
            syntax = "proto4";
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto1));
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto4));
    }

    @Test
    public void testValidPackageDeclaration() {
        String proto = """
            package foo.bar;
            """;
        var document = ProtobufParser.parseOnly(proto);
        assertEquals("foo.bar", document.packageName().orElse(null));
    }

    @Test
    public void testMultiplePackageDeclarationsError() {
        String proto = """
            package foo;
            package bar;
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackageNameValidity() {
        String valid = """
            package my_pkg.v1;
            """;
        String invalid = """
            package 1pkg.name;
            """;
        ProtobufParser.parseOnly(valid);
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(invalid));
    }

    @Test
    public void testBasicImportStatement() {
        String proto = """
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
        String proto = """
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
        String proto = """
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
        String proto = """
            import weaker "other.proto";
            """;
        var other = new ProtobufDocumentTree(Path.of("other.proto"));
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto, other));
    }

    @Test
    public void testInvalidImportPaths() {
        String proto = """
            import "missing;
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testUnknownImportPaths() {
        String proto = """
            import "other.proto";
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testStandardLiteralFileLevelOption() {
        String proto = """
                option java_package = "com.example.foo";
                """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("java_package", option.name());
        assertTrue(option.value() instanceof ProtobufLiteralExpression);
        assertEquals("com.example.foo", ((ProtobufLiteralExpression) option.value()).value());
    }

    @Test
    public void testStandardBoolTrueFileLevelOption() {
        String proto = """
            option java_multiple_files = true;
            """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("java_multiple_files", option.name());
        assertTrue(option.value() instanceof ProtobufBoolExpression);
        assertSame(true, ((ProtobufBoolExpression) option.value()).value());
    }

    @Test
    public void testStandardBoolFalseFileLevelOption() {
        String proto = """
            option java_multiple_files = false;
            """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("java_multiple_files", option.name());
        assertTrue(option.value() instanceof ProtobufBoolExpression);
        assertSame(false, ((ProtobufBoolExpression) option.value()).value());
    }

    @Test
    public void testStandardEnumConstantFileLevelOption() {
        String proto = """
            option optimize_for = SPEED;
            """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class).orElseThrow();
        assertEquals("optimize_for", option.name());
        assertTrue(option.value() instanceof ProtobufEnumConstantExpression);
        assertEquals("SPEED", ((ProtobufEnumConstantExpression) option.value()).name());
    }

    // There are no standard int, float or message file level options to test

    @Test
    public void testStandardFileLevelOptionWithParenthesisError() {
        String proto = """
            option (optimize_for) = SPEED;
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testCustomMessageFileLevelOption() {
        String proto = """
                message MyOption {
                  optional bool a = 1;
                }
                
                extend google.protobuf.FileOptions {
                  optional bool simple_option = 1000;
                  optional MyOption structured_option = 1001;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        String proto1 = """
            option (simple_option) = true;
            option (structured_option).a = true;
            """;
        ProtobufParser.parseOnly(proto1, document);
    }

    @Test
    public void testInvalidFileLevelOptionNamesOrValues() {
        String proto = """
            option abc = 123;
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));

        String proto1 = """
            option java_package = 123;
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto1));
    }

    @Test
    public void testEmptyMessageDefinition() {
        String proto = """
            message MyMessage {}
            """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByType(ProtobufMessageStatement.class).orElseThrow();
        assertEquals("MyMessage", message.name());
    }

    @Test
    public void testProto2MessageWithBasicScalarFields() {
        String proto = """
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
        String proto = """
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
        String proto = """
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
        String proto = """
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
        String proto = """
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
        String proto = """
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
        String proto = """
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
        String proto = """
            message M {
              required map<string, int32> map_field = 1;
            }
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnums() {
        String proto = """
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
        String proto = """
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
        String proto = """
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
        String proto = """
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
        String proto = """
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
        String proto = """
            message MessageType {
            
            }
            
            extend MessageType {
              optional string ext_field = 100;
            }
            """;
        var document = ProtobufParser.parseOnly(proto);
        var extend = document.getDirectChildrenByType(ProtobufMessageStatement.class)
                .filter(ProtobufMessageStatement::extension)
                .findFirst()
                .orElseThrow();
        assertEquals("MessageType", extend.name());
        var field = extend.getDirectChildByNameAndType("ext_field", ProtobufFieldStatement.class).orElseThrow();
        assertSame(ProtobufType.STRING, field.type().protobufType());
        assertEquals(Long.valueOf(100), field.index());
    }

    @Test
    public void testExtendDefinitionOnUnknownError() {
        String proto = """
            extend MessageType {
              optional string ext_field = 100;
            }
            """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }
}