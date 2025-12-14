package it.auties.protobuf.parser;

import it.auties.protobuf.parser.token.*;
import it.auties.protobuf.parser.number.ProtobufFloatingPoint;
import it.auties.protobuf.parser.number.ProtobufInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

public class ProtobufLexerTest {
    @Test
    void testNextRawToken_EOF_ThrowsException() {
        var tokenizer = new ProtobufLexer(new StringReader(""));
        assertThrows(IOException.class, () -> tokenizer.nextRawToken(true));
    }

    @Test
    void testNextRawToken_EOF_NoThrow_ReturnsNull() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(""));
        assertNull(tokenizer.nextRawToken(false));
    }

    @Test
    void testNextRawToken_SimpleWord() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("hello"));
        assertEquals("hello", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_MultipleWords() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("hello world foo"));
        assertEquals("hello", tokenizer.nextRawToken());
        assertEquals("world", tokenizer.nextRawToken());
        assertEquals("foo", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_SingleCharacter() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("{"));
        assertEquals("{", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_MultipleSpecialCharacters() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("{ } [ ] ( ) ; , ="));
        assertEquals("{", tokenizer.nextRawToken());
        assertEquals("}", tokenizer.nextRawToken());
        assertEquals("[", tokenizer.nextRawToken());
        assertEquals("]", tokenizer.nextRawToken());
        assertEquals("(", tokenizer.nextRawToken());
        assertEquals(")", tokenizer.nextRawToken());
        assertEquals(";", tokenizer.nextRawToken());
        assertEquals(",", tokenizer.nextRawToken());
        assertEquals("=", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_DoubleQuotedString() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello world\""));
        assertEquals("hello world", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_SingleQuotedString() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("'hello world'"));
        assertEquals("hello world", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_EmptyDoubleQuotedString() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"\""));
        assertEquals("", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_EmptySingleQuotedString() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("''"));
        assertEquals("", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_MultiPartStringDoubleQuotes() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello\" \"world\" \"!\""));
        assertEquals("helloworld!", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_MultiPartStringSingleQuotes() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("'hello' 'world' '!'"));
        assertEquals("helloworld!", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_MultiPartStringMixedQuotes() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello\" 'world' \"!\""));
        assertEquals("helloworld!", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_MultiPartStringWithEmptyParts() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello\" \"\" \"world\""));
        assertEquals("helloworld", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_StringFollowedByWord() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello\" world"));
        assertEquals("hello", tokenizer.nextRawToken());
        assertEquals("world", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_NumbersAsWords() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123 456.789 -42"));
        assertEquals("123", tokenizer.nextRawToken());
        assertEquals("456.789", tokenizer.nextRawToken());
        assertEquals("-42", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_WithComments_SlashSlash() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("hello // comment\nworld"));
        assertEquals("hello", tokenizer.nextRawToken());
        assertEquals("world", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_WithComments_SlashStar() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("hello /* comment */ world"));
        assertEquals("hello", tokenizer.nextRawToken());
        assertEquals("world", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_WithWhitespace() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("  hello   world  "));
        assertEquals("hello", tokenizer.nextRawToken());
        assertEquals("world", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_WithTabs() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\thello\t\tworld\t"));
        assertEquals("hello", tokenizer.nextRawToken());
        assertEquals("world", tokenizer.nextRawToken());
    }

    @Test
    void testNextRawToken_WithNewlines() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("hello\nworld\n"));
        assertEquals("hello", tokenizer.nextRawToken());
        assertEquals("world", tokenizer.nextRawToken());
    }

    @Test
    void testNextToken_TrueKeyword() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("true"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufBoolToken.class, token);
        assertTrue(((ProtobufBoolToken) token).value());
    }

    @Test
    void testNextToken_FalseKeyword() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("false"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufBoolToken.class, token);
        assertFalse(((ProtobufBoolToken) token).value());
    }

    @Test
    void testNextToken_PositiveInfinity() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("inf"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Infinity.class, number);
        assertEquals(ProtobufFloatingPoint.Infinity.Signum.POSITIVE, ((ProtobufFloatingPoint.Infinity) number).signum());
    }

    @Test
    void testNextToken_NegativeInfinity() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-inf"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Infinity.class, number);
        assertEquals(ProtobufFloatingPoint.Infinity.Signum.NEGATIVE, ((ProtobufFloatingPoint.Infinity) number).signum());
    }

    @Test
    void testNextToken_NaN() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("nan"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.NaN.class, number);
    }

    @Test
    void testNextToken_EmptyToken() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("", ((ProtobufLiteralToken) token).value());
    }

    @Test
    void testNextToken_PositiveDecimalInteger() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("12345"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("12345"), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_NegativeDecimalInteger() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-12345"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("-12345"), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_PositiveDecimalIntegerWithPlus() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+12345"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("12345"), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_Zero() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(BigInteger.ZERO, ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_SingleDigit() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("7"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("7"), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_VeryLargeDecimalInteger() throws IOException {
        var largeNumber = "123456789012345678901234567890123456789012345678901234567890";
        var tokenizer = new ProtobufLexer(new StringReader(largeNumber));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger(largeNumber), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_VeryLargeNegativeInteger() throws IOException {
        var largeNumber = "-123456789012345678901234567890123456789012345678901234567890";
        var tokenizer = new ProtobufLexer(new StringReader(largeNumber));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger(largeNumber), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_OctalInteger() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0755"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("755", 8), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_OctalIntegerNegative() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-0755"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("755", 8).negate(), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_OctalIntegerPositive() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+0755"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("755", 8), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_OctalZero() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("00"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(BigInteger.ZERO, ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_OctalOnlyValidDigits() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("01234567"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("1234567", 8), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_OctalInvalidDigit8_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("08"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("08", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_OctalInvalidDigit9_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("09"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("09", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_OctalInvalidDigitInMiddle_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0758"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("0758", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_LargeOctalNumber() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("077777777777777"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("77777777777777", 8), ((ProtobufInteger) number).value());
    }
    
    @Test
    void testNextToken_HexadecimalIntegerLowerX() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0x1a2b"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("1a2b", 16), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_HexadecimalIntegerUpperX() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0X1A2B"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("1A2B", 16), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_HexadecimalIntegerNegative() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-0xabc"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("abc", 16).negate(), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_HexadecimalIntegerPositive() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+0xabc"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("abc", 16), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_HexadecimalZero() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0x0"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(BigInteger.ZERO, ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_HexadecimalAllDigits() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0x0123456789abcdefABCDEF"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("0123456789abcdefABCDEF", 16), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_HexadecimalInvalidCharacter_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0x12g"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("0x12g", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_LargeHexadecimalNumber() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16), ((ProtobufInteger) number).value());
    }
    
    @Test
    void testNextToken_SimpleDecimal() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123.456"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("123.456"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_NegativeDecimal() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-123.456"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("-123.456"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_PositiveDecimal() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+123.456"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("123.456"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_DecimalStartingWithDot() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(".456"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("0.456"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_DecimalStartingWithDotNegative() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-.456"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("-0.456"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_DecimalZero() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0.0"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("0.0"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_DecimalWithTrailingZeros() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123.4560000"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("123.4560000"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_DecimalWithLeadingZeros() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("00123.456"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("123.456"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_VeryPreciseDecimal() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123.123456789012345678901234567890"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("123.123456789012345678901234567890"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_DecimalInvalidCharacter_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123.45a"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("123.45a", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_DecimalMultipleDots_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123.45.67"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("123.45.67", ((ProtobufRawToken) token).value());
    }
    
    @Test
    void testNextToken_ScientificNotation_PositiveExponent() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e10"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("1.23e10"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_NegativeExponent() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e-10"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("1.23e-10"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_PositiveExponentWithPlus() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e+10"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("1.23e+10"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_UppercaseE() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23E10"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("1.23E10"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_NoDecimalPart() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123e10"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("123e10"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_NegativeNumber() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-1.23e10"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("-1.23e10"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_ZeroExponent() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e0"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("1.23"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_LargeExponent() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e1000"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("1.23e1000"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_VeryLargeNegativeExponent() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e-1000"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("1.23e-1000"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_StartingWithDot() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(".23e10"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("0.23e10"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ScientificNotation_NoExponentDigits_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("1.23e", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_ScientificNotation_OnlyExponentSign_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e+"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("1.23e+", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_ScientificNotation_OnlyExponentMinusSign_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e-"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("1.23e-", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_ScientificNotation_InvalidExponentCharacter_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e10a"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("1.23e10a", ((ProtobufRawToken) token).value());
    }
    
    @Test
    void testNextToken_DoubleQuotedLiteral() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello world\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("hello world", ((ProtobufLiteralToken) token).value());
        assertEquals('"', ((ProtobufLiteralToken) token).delimiter());
    }

    @Test
    void testNextToken_SingleQuotedLiteral() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("'hello world'"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("hello world", ((ProtobufLiteralToken) token).value());
        assertEquals('\'', ((ProtobufLiteralToken) token).delimiter());
    }

    @Test
    void testNextToken_EmptyDoubleQuotedLiteral() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("", ((ProtobufLiteralToken) token).value());
        assertEquals('"', ((ProtobufLiteralToken) token).delimiter());
    }

    @Test
    void testNextToken_EmptySingleQuotedLiteral() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("''"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("", ((ProtobufLiteralToken) token).value());
        assertEquals('\'', ((ProtobufLiteralToken) token).delimiter());
    }

    @Test
    void testNextToken_MultiPartLiteralDoubleQuotes() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello\" \"world\" \"!\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("helloworld!", ((ProtobufLiteralToken) token).value());
        assertEquals('"', ((ProtobufLiteralToken) token).delimiter());
    }

    @Test
    void testNextToken_MultiPartLiteralSingleQuotes() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("'hello' 'world' '!'"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("helloworld!", ((ProtobufLiteralToken) token).value());
        assertEquals('\'', ((ProtobufLiteralToken) token).delimiter());
    }

    @Test
    void testNextToken_MultiPartLiteralMixedDelimiters() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello\" 'world' \"!\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("helloworld!", ((ProtobufLiteralToken) token).value());
        assertEquals('"', ((ProtobufLiteralToken) token).delimiter());
    }

    @Test
    void testNextToken_MultiPartLiteralWithEmptyParts() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello\" \"\" \"world\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("helloworld", ((ProtobufLiteralToken) token).value());
    }

    @Test
    void testNextToken_LiteralWithNumbers() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test123\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("test123", ((ProtobufLiteralToken) token).value());
    }

    @Test
    void testNextToken_LiteralWithSpecialChars() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"hello!@#$%^&*()\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("hello!@#$%^&*()", ((ProtobufLiteralToken) token).value());
    }
    
    @Test
    void testNextToken_RawSingleCharacter() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("{"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("{", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_RawMultipleSpecialCharacters() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("{ } [ ]"));
        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
    }

    @Test
    void testNextToken_RawIdentifier() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("my_identifier"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("my_identifier", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_RawIdentifierWithNumbers() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("test123abc"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("test123abc", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_RawIdentifierStartingWithUnderscore() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("_test"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("_test", ((ProtobufRawToken) token).value());
    }
    
    @Test
    void testNextToken_OnlyPlus_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("+", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_OnlyMinus_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("-", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_OnlyDot_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("."));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals(".", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_DotWithNonDigit_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(".abc"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals(".abc", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_HexWithoutDigits_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0x"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("0x", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_JustZeroX_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0X"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("0X", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_DoubleNegative_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("--123"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("--123", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_AlphaInDecimal_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123abc"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("123abc", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_MixedHexOctal_ReturnsInteger() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0123"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);

        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("123", 8), ((ProtobufInteger) number).value());
    }
    
    @Test
    void testNextToken_MaxLongValue() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(String.valueOf(Long.MAX_VALUE)));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_MinLongValue() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(String.valueOf(Long.MIN_VALUE)));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(BigInteger.valueOf(Long.MIN_VALUE), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_BeyondLongValue() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("9223372036854775808"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("9223372036854775808"), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_VeryLongDecimalPrecision() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0.00000000000000000000000000000000000001"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "inf", "-inf", "nan"})
    void testNextToken_KeywordsCaseSensitive(String keyword) throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(keyword));
        var token = tokenizer.nextToken();
        assertFalse(token instanceof ProtobufRawToken);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", "False", "INF", "-INF", "NaN", "NAN"})
    void testNextToken_KeywordsWrongCase_ReturnsRaw(String keyword) throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(keyword));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
    }

    @Test
    void testNextToken_SequentialNumbers() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123 456 789"));
        var token1 = tokenizer.nextToken();
        var token2 = tokenizer.nextToken();
        var token3 = tokenizer.nextToken();

        assertInstanceOf(ProtobufNumberToken.class, token1);
        assertInstanceOf(ProtobufNumberToken.class, token2);
        assertInstanceOf(ProtobufNumberToken.class, token3);
    }

    @Test
    void testNextToken_MixedTokenTypes() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("field: \"value\" 123 true"));

        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufLiteralToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufNumberToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufBoolToken.class, tokenizer.nextToken());
    }

    @Test
    void testNextToken_ComplexProtobufLikeInput() throws IOException {
        var input = "message: { field1: 123 field2: \"test\" field3: true field4: 0x1a field5: 3.14 }";
        var tokenizer = new ProtobufLexer(new StringReader(input));

        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufRawToken.class, tokenizer.nextToken());
        assertInstanceOf(ProtobufNumberToken.class, tokenizer.nextToken());
    }

    @Test
    void testNextToken_HundredZeros() throws IOException {
        var zeros = "0".repeat(100);
        var tokenizer = new ProtobufLexer(new StringReader(zeros));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
    }

    @Test
    void testNextToken_VeryLongHexNumber() throws IOException {
        var hex = "0x" + "F".repeat(200);
        var tokenizer = new ProtobufLexer(new StringReader(hex));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
    }

    @Test
    void testNextToken_VeryLongOctalNumber() throws IOException {
        var octal = "0" + "7".repeat(200);
        var tokenizer = new ProtobufLexer(new StringReader(octal));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
    }

    @Test
    void testNextToken_ExtremelyLargeExponent() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1.23e999999999"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
    }

    @Test
    void testNextToken_MultiPartStringWith100Parts() throws IOException {
        var parts = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            parts.append("\"part\"");
            if (i < 99) {
                parts.append(" ");
            }
        }
        var tokenizer = new ProtobufLexer(new StringReader(parts.toString()));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertEquals("part".repeat(100), ((ProtobufLiteralToken) token).value());
    }
    
    @Test
    void testNextToken_LeadingZerosDecimal() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("000123"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);

        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
    }

    @Test
    void testNextToken_ZeroPointZero() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0.0"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(BigDecimal.ZERO.setScale(1, RoundingMode.UNNECESSARY), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_JustDecimalPoint_ReturnsRaw() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+."));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
    }

    @Test
    void testNextToken_PlusZero() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+0"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(BigInteger.ZERO, ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_MinusZero() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-0"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(BigInteger.ZERO, ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_ScientificWithOnlyWholePart() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("5e2"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("5e2"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_ZeroWithDecimal() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0.123"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("0.123"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_HexZero() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("0x000"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(BigInteger.ZERO, ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_DecimalOnlyFraction() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(".999"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("0.999"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    @Test
    void testNextToken_NegativeDecimalOnlyFraction() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-.999"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
        assertEquals(new BigDecimal("-0.999"), ((ProtobufFloatingPoint.Finite) number).value());
    }

    // Additional comprehensive tests for spec compliance

    @Test
    void testNextToken_StringWithInvalidEscapeSequence() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test\\q\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_StringWithIncompleteHexEscape() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test\\x\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_StringWithIncompleteOctalEscape() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test\\0\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_StringWithValidEscapeBackslash() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test\\\\\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_StringWithValidEscapeQuote() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test\\\"inside\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_StringWithValidEscapeNewline() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test\\n\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_StringWithValidEscapeTab() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test\\t\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_StringWithValidEscapeCarriageReturn() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test\\r\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_TerminatedStringAfterEscape() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"escaped\\n\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_TerminatedComment() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("/* comment */ token"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("token", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_SingleTokenAfterWhitespace() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("   token"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("token", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_TokenAfterWhitespace() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("   \t\n\r  identifier"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("identifier", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_TokenAfterComments() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("// comment\n/* another */ token"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("token", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_MultipleConsecutiveSpecialChars() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("<<>>"));
        assertEquals("<", ((ProtobufRawToken) tokenizer.nextToken()).value());
        assertEquals("<", ((ProtobufRawToken) tokenizer.nextToken()).value());
        assertEquals(">", ((ProtobufRawToken) tokenizer.nextToken()).value());
        assertEquals(">", ((ProtobufRawToken) tokenizer.nextToken()).value());
    }

    @Test
    void testNextToken_NegativeZeroFloat() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("-0.0"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
    }

    @Test
    void testNextToken_PositiveZeroFloat() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+0.0"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
    }

    @Test
    void testNextToken_FloatWithTrailingDot() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("123."));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
    }

    @Test
    void testNextToken_LeadingPlusWithHex() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+0xFF"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("FF", 16), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_LeadingPlusWithOctal() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+0777"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufInteger.class, number);
        assertEquals(new BigInteger("777", 8), ((ProtobufInteger) number).value());
    }

    @Test
    void testNextToken_MinusSignAlone() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("- 123"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("-", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_PlusSignAlone() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("+ 123"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("+", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_UnicodeCharactersInString() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"Hello 世界 🌍\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
        assertTrue(((ProtobufLiteralToken) token).value().contains("世界"));
    }

    @Test
    void testNextToken_StringWithNullCharacter() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("\"test\\000null\""));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufLiteralToken.class, token);
    }

    @Test
    void testNextToken_VeryLongIdentifier() throws IOException {
        var longId = "a".repeat(1000);
        var tokenizer = new ProtobufLexer(new StringReader(longId));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals(longId, ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_NumberWithLeadingZerosAndDecimal() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("000.123"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
    }

    @Test
    void testNextToken_ScientificNotationWithLeadingDigit() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("1e10"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufNumberToken.class, token);
        var number = ((ProtobufNumberToken) token).value();
        assertInstanceOf(ProtobufFloatingPoint.Finite.class, number);
    }

    @Test
    void testNextToken_DotFollowedByIdentifier() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(".identifier"));
        var token1 = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token1);
        assertEquals(".identifier", ((ProtobufRawToken) token1).value());
    }

    @Test
    void testNextToken_ColonCharacter() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader(":"));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals(":", ((ProtobufRawToken) token).value());
    }

    @Test
    void testNextToken_LessThanGreaterThan() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("< >"));
        assertEquals("<", ((ProtobufRawToken) tokenizer.nextToken()).value());
        assertEquals(">", ((ProtobufRawToken) tokenizer.nextToken()).value());
    }

    @Test
    void testNextToken_SlashCharacter() throws IOException {
        var tokenizer = new ProtobufLexer(new StringReader("/ "));
        var token = tokenizer.nextToken();
        assertInstanceOf(ProtobufRawToken.class, token);
        assertEquals("/", ((ProtobufRawToken) token).value());
    }
}
