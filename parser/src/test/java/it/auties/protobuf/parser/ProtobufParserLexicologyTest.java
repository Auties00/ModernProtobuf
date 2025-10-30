package it.auties.protobuf.parser;

import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.tree.ProtobufBoolExpression;
import it.auties.protobuf.parser.tree.ProtobufFieldStatement;
import it.auties.protobuf.parser.tree.ProtobufMessageStatement;
import it.auties.protobuf.parser.tree.ProtobufNumberExpression;
import it.auties.protobuf.parser.type.ProtobufFloatingPoint;
import it.auties.protobuf.parser.type.ProtobufInteger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProtobufParserLexicologyTest {
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
        assertInstanceOf(ProtobufNumberExpression.class, aDefault.get().value());
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertInstanceOf(ProtobufInteger.class, aDefaultValue.value());
        assertEquals(BigInteger.valueOf(123), ((ProtobufInteger) aDefaultValue.value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertInstanceOf(ProtobufNumberExpression.class, bDefault.get().value());
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertInstanceOf(ProtobufInteger.class, bDefaultValue.value());
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
        assertInstanceOf(ProtobufNumberExpression.class, aDefault.get().value());
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertInstanceOf(ProtobufInteger.class, aDefaultValue.value());
        assertEquals(BigInteger.valueOf(63L), ((ProtobufInteger) aDefaultValue.value()).value());
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
        assertInstanceOf(ProtobufNumberExpression.class, aDefault.get().value());
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertInstanceOf(ProtobufInteger.class, aDefaultValue.value());
        assertEquals(BigInteger.valueOf(255), ((ProtobufInteger) aDefaultValue.value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertInstanceOf(ProtobufNumberExpression.class, bDefault.get().value());
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertInstanceOf(ProtobufInteger.class, bDefaultValue.value());
        assertEquals(BigInteger.valueOf(16), ((ProtobufInteger) bDefaultValue.value()).value());
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
        assertInstanceOf(ProtobufNumberExpression.class, aDefault.get().value());
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, aDefaultValue.value());
        assertEquals(BigDecimal.valueOf(1.0), ((ProtobufFloatingPoint.Finite) aDefaultValue.value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertInstanceOf(ProtobufNumberExpression.class, bDefault.get().value());
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, bDefaultValue.value());
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
        assertInstanceOf(ProtobufNumberExpression.class, aDefault.get().value());
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, aDefaultValue.value());
        assertEquals(BigDecimal.valueOf(-3.14e-5d), ((ProtobufFloatingPoint.Finite) aDefaultValue.value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertInstanceOf(ProtobufNumberExpression.class, bDefault.get().value());
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, bDefaultValue.value());
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
        assertInstanceOf(ProtobufNumberExpression.class, aDefault.get().value());
        var aDefaultValue = (ProtobufNumberExpression) aDefault.get().value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, aDefaultValue.value());
        assertEquals(BigDecimal.valueOf(.5), ((ProtobufFloatingPoint.Finite) aDefaultValue.value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertInstanceOf(ProtobufNumberExpression.class, bDefault.get().value());
        var bDefaultValue = (ProtobufNumberExpression) bDefault.get().value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, bDefaultValue.value());
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
        assertInstanceOf(ProtobufBoolExpression.class, aDefault.get().value());
        assertEquals(true, ((ProtobufBoolExpression) aDefault.get().value()).value());

        var b = document.getAnyChildByNameAndType("b", ProtobufFieldStatement.class);
        assertTrue(b.isPresent());
        var bDefault = b.get().getOption("default");
        assertTrue(bDefault.isPresent());
        assertInstanceOf(ProtobufBoolExpression.class, bDefault.get().value());
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
        var document = ProtobufParser.parseOnly(proto);
        var message = document.getDirectChildByNameAndType("messageLike", ProtobufMessageStatement.class).orElseThrow();
        assertNotNull(message);
    }

    @Test
    public void testIntegerZero() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int32 a = 1 [default = 0];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testIntegerLargePositive() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int64 a = 1 [default = 9223372036854775807];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testIntegerLargeNegative() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int64 a = 1 [default = -9223372036854775808];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testOctalWithLeadingZeros() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int32 a = 1 [default = 00077];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testHexUppercaseLetters() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int32 a = 1 [default = 0xABCDEF];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testHexLowercaseLetters() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int32 a = 1 [default = 0xabcdef];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testHexMixedCase() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional int32 a = 1 [default = 0xAbCdEf];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFloatWithExponentUppercaseE() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = 1.23E5];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFloatWithExponentLowercaseE() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = 1.23e5];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFloatWithPositiveExponent() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = 1.5e+10];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFloatNoDecimalPart() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = 5e10];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFloatTrailingDecimal() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = 5.];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFloatInfPositive() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = inf];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFloatInfNegative() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = -inf];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFloatNaN() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional float a = 1 [default = nan];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringAllCommonEscapes() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\a\\b\\f\\n\\r\\t\\v\\\\\\'\\""];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringUnicodeEscapeShort() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\u0041"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringUnicodeEscapeLong() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\U00000041"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringOctalEscapeOneDigit() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\7"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringOctalEscapeTwoDigits() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\77"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringOctalEscapeThreeDigits() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\377"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringHexEscapeLowercase() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\x41\\x42"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringHexEscapeUppercase() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "\\x41\\x42"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringConcatenationSameQuotes() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "hello " "world"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringConcatenationMixedQuotes() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "hello " 'world'];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringConcatenationMultipleParts() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "part1" "part2" "part3"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringEmptyString() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = ""];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringUTF8Characters() {
        var proto = """
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "Hello 世界 🌍"];
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testStringVeryLong() {
        var longString = "a".repeat(1000);
        var proto = String.format("""
                syntax = "proto2";
                message M {
                    optional string a = 1 [default = "%s"];
                }
                """, longString);
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testIdentifierMaxUnderscores() {
        var proto = """
                syntax = "proto3";
                message ___message___ {
                    string ___field___ = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testIdentifierAllCaps() {
        var proto = """
                syntax = "proto3";
                message ALLMESSAGE {
                    string ALLFIELD = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testIdentifierAllLower() {
        var proto = """
                syntax = "proto3";
                message allmessage {
                    string allfield = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testIdentifierMixedCase() {
        var proto = """
                syntax = "proto3";
                message MyMessageType {
                    string myFieldName = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testIdentifierVeryLong() {
        var longName = "a".repeat(200);
        var proto = String.format("""
                syntax = "proto3";
                message %s {
                    string field = 1;
                }
                """, longName);
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testFullIdentifierWithPackage() {
        var proto = """
                syntax = "proto3";
                package com.example.app.v1.models;
                message M {
                    string field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertEquals("com.example.app.v1.models", document.packageName().orElse(null));
    }

    @Test
    public void testIdentifierSingleLetter() {
        var proto = """
                syntax = "proto3";
                message M {
                    string a = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testIdentifierSingleUnderscore() {
        var proto = """
                syntax = "proto3";
                message M {
                    string _ = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testNestedBlockCommentsError() {
        var proto = """
                syntax = "proto3";
                /* outer /* inner */ still comment? */
                message M {
                    string field = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testBlockCommentSpanningMultipleLines() {
        var proto = """
                syntax = "proto3";
                /*
                 * This is a
                 * multi-line
                 * block comment
                 * spanning several lines
                 */
                message M {
                    string field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testSingleLineCommentAtEndOfFile() {
        var proto = """
                syntax = "proto3";
                message M {
                    string field = 1;
                }
                // Comment at end with no newline""";
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testCommentWithSpecialCharacters() {
        var proto = """
                syntax = "proto3";
                // Comment with special chars: !@#$%^&*(){}[]<>?/\\|`~
                message M {
                    string field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }

    @Test
    public void testCommentWithUnicode() {
        var proto = """
                syntax = "proto3";
                // Comment with Unicode: 你好世界 🌍
                message M {
                    string field = 1;
                }
                """;
        var document = ProtobufParser.parseOnly(proto);
        assertNotNull(document);
    }
}
