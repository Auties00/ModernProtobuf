
package it.auties.protobuf.parser;

import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.tree.ProtobufBoolExpression;
import it.auties.protobuf.parser.tree.ProtobufFieldStatement;
import it.auties.protobuf.parser.tree.ProtobufMessageStatement;
import it.auties.protobuf.parser.tree.ProtobufNumberExpression;
import it.auties.protobuf.parser.type.ProtobufFloatingPoint;
import it.auties.protobuf.parser.type.ProtobufInteger;
import org.junit.Test;
import org.junit.jupiter.api.Nested;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.*;

@Nested
public class ProtobufParserLexicalTests {
    // 2.1.1 Identifiers
    @Test
    public void testValidSingleWordIdentifiers() {
        var proto = """
                syntax = "proto3";
                message MyMessage {
                    string fieldName = 1;
                    string _some_value = 2;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("MyMessage", ProtobufMessageStatement.class).orElseThrow();
        assertNotNull(message);
        assertEquals("MyMessage", message.name());
    }

    @Test
    public void testValidIdentifiersWithDigits() {
        var proto = """
                syntax = "proto3";
                message Message123 {
                    string field_4_data = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("Message123", ProtobufMessageStatement.class).orElseThrow();
        assertNotNull(message);
    }

    @Test
    public void testValidIdentifiersWithUnderscores() {
        var proto = """
                syntax = "proto3";
                message my_message {
                    string _hidden_field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("my_message", ProtobufMessageStatement.class).orElseThrow();
        assertNotNull(message);
    }

    @Test
    public void testValidFullIdentifiers() {
        var proto = """
                syntax = "proto3";
                package com.example.foo;
                message MyMessage {
                    string field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertEquals("com.example.foo", document.packageName().orElse(null));
    }

    @Test
    public void testInvalidIdentifiersStartingWithDigits() {
        var proto = """
                syntax = "proto3";
                message 123Invalid {
                    string field = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testInvalidIdentifiersContainingDashes() {
        var proto = """
                syntax = "proto3";
                message ValidMessage {
                    string field-name = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testInvalidIdentifiersWithOtherIllegalCharacters() {
        var proto = """
                syntax = "proto3";
                message Invalid@Message {
                    string field = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    // 2.1.2 Literals (Integers, Floats, Booleans, Strings)
    @Test
    public void testIntegerDecimalLiterals() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int32 a = 1 [default = 123];
                    optional int32 b = 2 [default = -45];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
        
        var a = document.getAnyChildByNameAndType("a", ProtobufFieldStatement.class);
        assertTrue(a.isPresent());
        var aDefault = a.get().getOption("default");
        assertTrue(aDefault.isPresent());
        assertTrue(aDefault.get().value() instanceof ProtobufNumberExpression);
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertTrue(aDefaultValue.value() instanceof ProtobufInteger);
        assertEquals(BigInteger.valueOf(123), ((ProtobufInteger) aDefaultValue.value()).value());
       
        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertTrue(bDefault.get().value() instanceof ProtobufNumberExpression);
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertTrue(bDefaultValue.value() instanceof ProtobufInteger);
        assertEquals(BigInteger.valueOf(-45), ((ProtobufInteger) bDefaultValue.value()).value());
    }

    @Test
    public void testIntegerOctalLiterals() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int32 a = 1 [default = 077];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);

        var a = document.getAnyChildByNameAndType("a", ProtobufFieldStatement.class);
        assertTrue(a.isPresent());
        var aDefault = a.get().getOption("default");
        assertTrue(aDefault.isPresent());
        assertTrue(aDefault.get().value() instanceof ProtobufNumberExpression);
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertTrue(aDefaultValue.value() instanceof ProtobufInteger);
        assertEquals(BigInteger.valueOf(63L), ((ProtobufInteger) aDefaultValue.value()).value()); // 077 octal = 63 decimal
    }

    @Test
    public void testIntegerHexadecimalLiterals() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int32 a = 1 [default = 0xFF];
                    optional int32 b = 2 [default = 0x10];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);

        var a = document.getAnyChildByNameAndType("a", ProtobufFieldStatement.class);
        assertTrue(a.isPresent());
        var aDefault = a.get().getOption("default");
        assertTrue(aDefault.isPresent());
        assertTrue(aDefault.get().value() instanceof ProtobufNumberExpression);
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertTrue(aDefaultValue.value() instanceof ProtobufInteger);
        assertEquals(BigInteger.valueOf(255), ((ProtobufInteger) aDefaultValue.value()).value()); // 0xFF = 255

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertTrue(bDefault.get().value() instanceof ProtobufNumberExpression);
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertTrue(bDefaultValue.value() instanceof ProtobufInteger);
        assertEquals(BigInteger.valueOf(16), ((ProtobufInteger) bDefaultValue.value()).value()); // 0x10 = 16
    }

    @Test
    public void testFloatingPointStandardDecimalLiterals() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = 1.0];
                    optional double b = 2 [default = 3.14];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);

        var a = document.getAnyChildByNameAndType("a", ProtobufFieldStatement.class);
        assertTrue(a.isPresent());
        var aDefault = a.get().getOption("default");
        assertTrue(aDefault.isPresent());
        assertTrue(aDefault.get().value() instanceof ProtobufNumberExpression);
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertTrue(aDefaultValue.value() instanceof ProtobufFloatingPoint.Finite);
        assertEquals(BigDecimal.valueOf(1.0), ((ProtobufFloatingPoint.Finite) aDefaultValue.value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertTrue(bDefault.get().value() instanceof ProtobufNumberExpression);
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertTrue(bDefaultValue.value() instanceof ProtobufFloatingPoint.Finite);
        assertEquals(BigDecimal.valueOf(3.14), ((ProtobufFloatingPoint.Finite) bDefaultValue.value()).value());
    }

    @Test
    public void testFloatingPointScientificNotationLiterals() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = -3.14e-5];
                    optional double b = 2 [default = 1.23e10];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);

        var a = document.getAnyChildByNameAndType("a", ProtobufFieldStatement.class);
        assertTrue(a.isPresent());
        var aDefault = a.get().getOption("default");
        assertTrue(aDefault.isPresent());
        assertTrue(aDefault.get().value() instanceof ProtobufNumberExpression);
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertTrue(aDefaultValue.value() instanceof ProtobufFloatingPoint.Finite);
        assertEquals(BigDecimal.valueOf(-3.14e-5d), ((ProtobufFloatingPoint.Finite) aDefaultValue.value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertTrue(bDefault.get().value() instanceof ProtobufNumberExpression);
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertTrue(bDefaultValue.value() instanceof ProtobufFloatingPoint.Finite);
        assertEquals(BigDecimal.valueOf(1.23e10d), ((ProtobufFloatingPoint.Finite) bDefaultValue.value()).value());
    }

    @Test
    public void testFloatingPointLeadingDecimalLiterals() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = .5];
                    optional double b = 2 [default = .25];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);

        var a = document.getAnyChildByNameAndType("a", ProtobufFieldStatement.class);
        assertTrue(a.isPresent());
        var aDefault = a.get().getOption("default");
        assertTrue(aDefault.isPresent());
        assertTrue(aDefault.get().value() instanceof ProtobufNumberExpression);
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertTrue(aDefaultValue.value() instanceof ProtobufFloatingPoint.Finite);
        assertEquals(BigDecimal.valueOf(.5), ((ProtobufFloatingPoint.Finite) aDefaultValue.value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertTrue(bDefault.get().value() instanceof ProtobufNumberExpression);
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertTrue(bDefaultValue.value() instanceof ProtobufFloatingPoint.Finite);
        assertEquals(BigDecimal.valueOf(.25), ((ProtobufFloatingPoint.Finite) bDefaultValue.value()).value());
    }

    @Test
    public void testFloatingPointSpecialValues() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = inf];
                    optional float b = 2 [default = -inf];
                    optional float c = 3 [default = nan];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testBooleanLiterals() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional bool a = 1 [default = true];
                    optional bool b = 2 [default = false];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);

        var a = document.getAnyChildByNameAndType("a", ProtobufFieldStatement.class);
        assertTrue(a.isPresent());
        var aDefault = a.get().getOption("default");
        assertTrue(aDefault.isPresent());
        assertTrue(aDefault.get().value() instanceof ProtobufBoolExpression);
        assertEquals(true, ((ProtobufBoolExpression) aDefault.get().value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertTrue(bDefault.get().value() instanceof ProtobufBoolExpression);
        assertEquals(false, ((ProtobufBoolExpression) bDefault.get().value()).value());
    }

    @Test
    public void testStringLiteralsSingleQuotes() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = 'hello'];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringLiteralsDoubleQuotes() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "hello"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringLiteralsHexadecimalEscapeSequences() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\x48\\x65\\x6C\\x6C\\x6F"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringLiteralsOctalEscapeSequences() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\101\\102\\103"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringLiteralsUnicodeEscapeSequences() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\u0048\\u0065\\u006C\\u006C\\u006F"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringLiteralsCommonCharacterEscapes() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "line1\\nline2"];
                    optional string b = 2 [default = "tab\\there"];
                    optional string c = 3 [default = "back\\\\slash"];
                    optional string d = 4 [default = "quote\\"here"];
                    optional string e = 5 [default = 'apos\\'here'];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testMultiPartStringLiterals() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "hello "
                                                     "world"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    // 2.1.3 Comments and Whitespace
    @Test
    public void testSingleLineComments() {
        var proto = """
                syntax = "proto3";
                // This is a comment
                message M {
                    string field = 1; // another comment
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("M", ProtobufMessageStatement.class).orElseThrow();
        assertNotNull(message);
    }

    @Test
    public void testMultiLineComments() {
        var proto = """
                syntax = "proto3";
                /* This is a
                   multi-line
                   comment */
                message M {
                    string field = 1; /* inline block comment */
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("M", ProtobufMessageStatement.class).orElseThrow();
        assertNotNull(message);
    }

    @Test
    public void testMixedCommentsAndCode() {
        var proto = """
                syntax = "proto3";
                // Comment before message
                /* Block comment */
                message M {
                    // Comment before field
                    string field = 1; // After field
                    /* Before another field */
                    int32 number = 2;
                }
                // End comment
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("M", ProtobufMessageStatement.class).orElseThrow();
        assertEquals(2, message.children().size());
    }

    @Test
    public void testVariousWhitespaceForms() {
        var proto = """
                syntax="proto3";
                
                message\tM\t{
                \tstring\tfield\t=\t1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("M", ProtobufMessageStatement.class).orElseThrow();
        assertNotNull(message);
    }

    @Test
    public void testKeywordsNotTreatedAsIdentifiers() {
        var proto = """
                syntax = "proto3";
                message M {
                    string message = 1;
                }
                """;
        ProtobufParser.parseOnly(proto);
    }

    @Test
    public void testIdentifiersNotTreatedAsKeywords() {
        var proto = """
                syntax = "proto3";
                message messageLike {
                    string field = 1;
                }
                """;
        // Should parse successfully as "messageLike" is not exactly "message"
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("messageLike", ProtobufMessageStatement.class).orElseThrow();
        assertNotNull(message);
    }
}