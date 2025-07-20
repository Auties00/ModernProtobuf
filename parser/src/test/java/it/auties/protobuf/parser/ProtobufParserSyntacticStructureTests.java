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
        var importStatement = document.getDirectChildByType(ProtobufImportStatement.class);
        assertTrue(importStatement.isPresent());
        assertSame(ProtobufImportStatement.Modifier.NONE, importStatement.get().modifier());
        assertEquals("other.proto", importStatement.get().location());
        assertSame(other, importStatement.get().document());
    }

    @Test
    public void testPublicImportStatement() {
        String proto = """
            import public "other.proto";
            """;
        var other = new ProtobufDocumentTree(Path.of("other.proto"));
        var document = ProtobufParser.parseOnly(proto, other);
        var importStatement = document.getDirectChildByType(ProtobufImportStatement.class);
        assertTrue(importStatement.isPresent());
        assertSame(ProtobufImportStatement.Modifier.PUBLIC, importStatement.get().modifier());
        assertEquals("other.proto", importStatement.get().location());
        assertSame(other, importStatement.get().document());
    }

    @Test
    public void testWeakImportStatement() {
        String proto = """
            import weak "other.proto";
            """;
        var other = new ProtobufDocumentTree(Path.of("other.proto"));
        var document = ProtobufParser.parseOnly(proto, other);
        var importStatement = document.getDirectChildByType(ProtobufImportStatement.class);
        assertTrue(importStatement.isPresent());
        assertSame(ProtobufImportStatement.Modifier.WEAK, importStatement.get().modifier());
        assertEquals("other.proto", importStatement.get().location());
        assertSame(other, importStatement.get().document());
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
        var option = document.getDirectChildByType(ProtobufOptionStatement.class);
        assertTrue(option.isPresent());
        assertEquals("java_package", option.get().name());
        assertTrue(option.get().value() instanceof ProtobufLiteralExpression);
        assertEquals("com.example.foo", ((ProtobufLiteralExpression) option.get().value()).value());
    }

    @Test
    public void testStandardBoolTrueFileLevelOption() {
        String proto = """
            option java_multiple_files = true;
            """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class);
        assertTrue(option.isPresent());
        assertEquals("java_multiple_files", option.get().name());
        assertTrue(option.get().value() instanceof ProtobufBoolExpression);
        assertSame(true, ((ProtobufBoolExpression) option.get().value()).value());
    }

    @Test
    public void testStandardBoolFalseFileLevelOption() {
        String proto = """
            option java_multiple_files = false;
            """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class);
        assertTrue(option.isPresent());
        assertEquals("java_multiple_files", option.get().name());
        assertTrue(option.get().value() instanceof ProtobufBoolExpression);
        assertSame(false, ((ProtobufBoolExpression) option.get().value()).value());
    }

    @Test
    public void testStandardEnumConstantFileLevelOption() {
        String proto = """
            option optimize_for = SPEED;
            """;
        var document = ProtobufParser.parseOnly(proto);
        var option = document.getDirectChildByType(ProtobufOptionStatement.class);
        assertTrue(option.isPresent());
        assertEquals("optimize_for", option.get().name());
        assertTrue(option.get().value() instanceof ProtobufEnumConstantExpression);
        assertEquals("SPEED", ((ProtobufEnumConstantExpression) option.get().value()).name());
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
        var message = document.getDirectChildByType(ProtobufMessageStatement.class);
        assertTrue(message.isPresent());
        assertEquals("MyMessage", message.get().name());
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
        var message = document.getDirectChildByType(ProtobufMessageStatement.class);
        assertTrue(message.isPresent());
        assertEquals("M", message.get().name());
        assertSame(2, message.get().children().size());
        var firstField = message.get().getDirectChildByIndexAndType(1, ProtobufFieldStatement.class);
        assertTrue(firstField.isPresent());
        assertSame(ProtobufFieldStatement.Modifier.OPTIONAL, firstField.get().modifier());
        assertTrue(firstField.get().type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.INT32, firstField.get().type().protobufType());
        assertEquals("id", firstField.get().name());
        assertEquals((Long) 1L, firstField.get().index());
        var secondField = message.get().getDirectChildByIndexAndType(2, ProtobufFieldStatement.class);
        assertTrue(secondField.isPresent());
        assertSame(ProtobufFieldStatement.Modifier.OPTIONAL, secondField.get().modifier());
        assertTrue(secondField.get().type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.STRING, secondField.get().type().protobufType());
        assertEquals("name", secondField.get().name());
        assertEquals((Long) 2L, secondField.get().index());
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
        var message = document.getDirectChildByType(ProtobufMessageStatement.class);
        assertTrue(message.isPresent());
        assertEquals("M", message.get().name());
        assertSame(2, message.get().children().size());
        var firstField = message.get().getDirectChildByIndexAndType(1, ProtobufFieldStatement.class);
        assertTrue(firstField.isPresent());
        assertEquals(ProtobufFieldStatement.Modifier.NONE, firstField.get().modifier());
        assertTrue(firstField.get().type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.INT32, firstField.get().type().protobufType());
        assertEquals("id", firstField.get().name());
        assertEquals((Long) 1L, firstField.get().index());
        var secondField = message.get().getDirectChildByIndexAndType(2, ProtobufFieldStatement.class);
        assertTrue(secondField.isPresent());
        assertEquals(ProtobufFieldStatement.Modifier.NONE, secondField.get().modifier());
        assertTrue(secondField.get().type() instanceof ProtobufPrimitiveTypeReference);
        assertSame(ProtobufType.STRING, secondField.get().type().protobufType());
        assertEquals("name", secondField.get().name());
        assertEquals((Long) 2L, secondField.get().index());
    }

    @Test
    public void testMessageWithNestedMessages() {
        String proto = """
            message M {
              message N {}
            }
            """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("M", ProtobufMessageStatement.class);
        assertTrue(message.isPresent());
        assertSame(1, message.get().children().size());
        var nestedMessage = message.get().getDirectChildByNameAndType("N", ProtobufMessageStatement.class);
        assertTrue(nestedMessage.isPresent());
        assertSame(0, nestedMessage.get().children().size());
    }

    @Test
    public void testMessageWithEnums() {
        String proto = """
            message M {
              enum E { A = 0; }
            }
            """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("M", ProtobufMessageStatement.class);
        assertTrue(message.isPresent());
        assertSame(1, message.get().children().size());
        var nestedEnum = message.get().getDirectChildByNameAndType("E", ProtobufEnumStatement.class);
        assertTrue(nestedEnum.isPresent());
        assertSame(1, nestedEnum.get().children().size());
        var enumConstant = nestedEnum.get().getDirectChildByIndexAndType(0, ProtobufEnumConstantStatement.class);
        assertTrue(enumConstant.isPresent());
        assertEquals("A", enumConstant.get().name());
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
        var opt = enumStmt.getDirectChildByType(ProtobufOptionStatement.class);
        assertTrue(opt.isPresent());
        assertTrue(opt.get().value() instanceof ProtobufBoolExpression);
        assertSame(true, ((ProtobufBoolExpression) opt.get().value()).value());
        var constant = enumStmt.getDirectChildByNameAndType("A", ProtobufEnumConstantStatement.class).orElseThrow();
        assertEquals(Long.valueOf(0), constant.index());
    }

    @Test
    public void testServices() {
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
        assertEquals("Response", method.outputType().value().name());
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
                .filter(ProtobufMessageStatement::isExtension)
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