package it.auties.protobuf.parser;

import it.auties.protobuf.parser.exception.ProtobufLexerException;
import it.auties.protobuf.parser.token.*;
import it.auties.protobuf.parser.number.ProtobufFloatingPoint;
import it.auties.protobuf.parser.number.ProtobufFloatingPoint.Infinity.Signum;
import it.auties.protobuf.parser.number.ProtobufInteger;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Performs lexical analysis (tokenization) of Protocol Buffer definition files.
 * <p>
 * The lexer is responsible for breaking down the raw character stream of a Protocol Buffer file
 * into a sequence of meaningful tokens that can be processed by the parser. It handles:
 * </p>
 * <ul>
 *   <li>Keyword recognition ({@code message}, {@code enum}, {@code service}, etc.)</li>
 *   <li>Identifier parsing (user-defined names for messages, fields, etc.)</li>
 *   <li>Numeric literal parsing with support for:
 *       <ul>
 *         <li>Decimal, hexadecimal (0x), and octal (0) integers</li>
 *         <li>Floating-point numbers with decimal and scientific notation</li>
 *         <li>Special values: {@code inf}, {@code -inf}, {@code nan}</li>
 *       </ul>
 *   </li>
 *   <li>String literal parsing with automatic concatenation of adjacent literals</li>
 *   <li>Boolean literals ({@code true}, {@code false})</li>
 *   <li>Comment removal (both {@code //} and {@code /* *}{@code /} styles)</li>
 *   <li>Operator and delimiter recognition ({@code {}, {@code }}, {@code ;}, {@code =}, etc.)</li>
 * </ul>
 * <p>
 * The lexer uses arbitrary-precision arithmetic ({@link BigInteger} and {@link BigDecimal})
 * to ensure that numeric literals are represented exactly as written in the source file,
 * without loss of precision.
 * </p>
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * Reader input = new FileReader("message.proto");
 * ProtobufLexer lexer = new ProtobufLexer(input);
 * ProtobufToken token;
 * while ((token = lexer.nextToken()) != null) {
 *     // Process token
 * }
 * }</pre>
 *
 * @see ProtobufToken
 * @see ProtobufParser
 */
public final class ProtobufLexer {
    private static final char STRING_LITERAL_DELIMITER = '"';
    private static final char STRING_LITERAL_ALIAS_DELIMITER = '\'';

    private static final String POSITIVE_INFINITY_KEYWORD = "inf";
    private static final ProtobufToken POSITIVE_INFINITY = new ProtobufNumberToken(new ProtobufFloatingPoint.Infinity(Signum.POSITIVE));

    private static final String NEGATIVE_INFINITY_TOKEN = "-inf";
    private static final ProtobufToken NEGATIVE_INFINITY = new ProtobufNumberToken(new ProtobufFloatingPoint.Infinity(Signum.NEGATIVE));

    private static final String NOT_A_NUMBER_TOKEN = "nan";
    private static final ProtobufToken NOT_A_NUMBER = new ProtobufNumberToken(new ProtobufFloatingPoint.NaN());

    private static final String TRUE_TOKEN = "true";
    private static final ProtobufToken TRUE = new ProtobufBoolToken(true);

    private static final String FALSE_TOKEN = "false";
    private static final ProtobufToken FALSE = new ProtobufBoolToken(false);

    private static final String EMPTY_TOKEN = "";
    private static final ProtobufToken EMPTY = new ProtobufRawToken(EMPTY_TOKEN);

    private static final BigInteger OCTAL_RADIX = BigInteger.valueOf(8);
    private static final BigInteger DECIMAL_RADIX = BigInteger.valueOf(10);
    private static final BigInteger HEXADECIMAL_RADIX = BigInteger.valueOf(16);

    private final StreamTokenizer tokenizer;

    /**
     * Constructs a new Protocol Buffer lexer for the given character stream.
     * <p>
     * The lexer configures a {@link StreamTokenizer} with Protocol Buffer-specific syntax rules
     * including comment handling, quote characters, and word character definitions.
     * </p>
     *
     * @param reader the character stream to tokenize, must not be null
     */
    public ProtobufLexer(Reader reader) {
        this.tokenizer = new StreamTokenizer(reader);
        tokenizer.resetSyntax();
        tokenizer.wordChars('a', 'z');
        tokenizer.wordChars('A', 'Z');
        tokenizer.wordChars(128 + 32, 255);
        tokenizer.wordChars('_', '_');
        tokenizer.wordChars('"', '"');
        tokenizer.wordChars('\'', '\'');
        tokenizer.wordChars('.', '.');
        tokenizer.wordChars('-', '-');
        tokenizer.wordChars('+', '+');
        for (int i = '0'; i <= '9'; i++) {
            tokenizer.wordChars(i, i);
        }
        tokenizer.whitespaceChars(0, ' ');
        tokenizer.slashSlashComments(true);
        tokenizer.slashStarComments(true);
        tokenizer.quoteChar(STRING_LITERAL_DELIMITER);
        tokenizer.quoteChar(STRING_LITERAL_ALIAS_DELIMITER);
    }

    /**
     * Moves back the tokenizer to the previous token
     */
    public void moveToPreviousToken() {
        tokenizer.pushBack();
    }

    /**
     * Reads the next raw token as a string, throwing an exception if the end of input is reached.
     * <p>
     * This method reads tokens without interpreting them as specific types (numbers, booleans, etc.).
     * It is primarily used internally by the parser for lookahead operations.
     * </p>
     *
     * @return the next raw token string
     * @throws IOException if an I/O error occurs or the end of input is reached unexpectedly
     */
    public String nextRawToken() throws IOException {
        return nextRawToken(true);
    }

    /**
     * Reads the next raw token as a string, optionally throwing an exception on end of input.
     *
     * @param throwsOnEof if true, throws IOException when end of input is reached; if false, returns null
     * @return the next raw token string, or null if end of input is reached and throwsOnEof is false
     * @throws IOException if an I/O error occurs or (when throwsOnEof is true) the end of input is reached
     */
    public String nextRawToken(boolean throwsOnEof) throws IOException {
        var token = tokenizer.nextToken();
        return switch (token) {
            case StreamTokenizer.TT_EOF -> {
                if(throwsOnEof) {
                    throw new IOException("Unexpected end of input");
                }else {
                    yield null;
                }
            }
            case StreamTokenizer.TT_WORD -> tokenizer.sval;
            case STRING_LITERAL_DELIMITER, STRING_LITERAL_ALIAS_DELIMITER -> parseMultiPartStringToken(tokenizer.sval);
            default -> String.valueOf((char) token);
        };
    }

    private String parseMultiPartStringToken(String head) throws IOException {
        var token = tokenizer.nextToken();
        if(!isStringDelimiter(token)) {
            moveToPreviousToken();
            return head;
        }else {
            var result = new StringBuilder();
            result.append(head);
            do {
                result.append(tokenizer.sval);
            } while (isStringDelimiter((tokenizer.nextToken())));
            moveToPreviousToken();
            return result.toString();
        }
    }

    private boolean isStringDelimiter(int token) {
        return token == STRING_LITERAL_DELIMITER || token == STRING_LITERAL_ALIAS_DELIMITER;
    }

    /**
     * Reads and returns the next typed token from the input stream.
     * <p>
     * This method performs full lexical analysis, recognizing and categorizing tokens into their
     * appropriate types ({@link ProtobufNumberToken}, {@link ProtobufBoolToken}, {@link ProtobufLiteralToken},
     * or {@link ProtobufRawToken}). It handles special numeric formats, boolean keywords, string literals
     * with concatenation, and arbitrary-precision number parsing.
     * </p>
     *
     * @return the next token from the input stream
     * @throws IOException if an I/O error occurs
     * @throws ProtobufLexerException if a lexical error is encountered (e.g., unexpected end of input)
     */
    public ProtobufToken nextToken() throws IOException {
        var token = tokenizer.nextToken();
        return switch (token) {
            case StreamTokenizer.TT_EOL -> throw new ProtobufLexerException("Unexpected end of input while tokenizing\n\nThe lexer encountered an unexpected end of line or end of file.\nThis usually means your .proto file is incomplete or has a syntax error.\n\nHelp: Check that all statements are properly terminated with semicolons (;)\n      and all blocks are properly closed with curly braces (})", tokenizer.lineno());
            case StreamTokenizer.TT_WORD -> parseWord(tokenizer.sval);
            case STRING_LITERAL_DELIMITER -> new ProtobufLiteralToken(parseMultiPartStringToken(tokenizer.sval), STRING_LITERAL_DELIMITER);
            case STRING_LITERAL_ALIAS_DELIMITER -> new ProtobufLiteralToken(parseMultiPartStringToken(tokenizer.sval), STRING_LITERAL_ALIAS_DELIMITER);
            default -> new ProtobufRawToken(String.valueOf((char) token));
        };
    }

    private static ProtobufToken parseWord(String token) {
        return switch (token) {
            case EMPTY_TOKEN -> EMPTY;
            case TRUE_TOKEN -> TRUE;
            case FALSE_TOKEN -> FALSE;
            case POSITIVE_INFINITY_KEYWORD -> POSITIVE_INFINITY;
            case NEGATIVE_INFINITY_TOKEN -> NEGATIVE_INFINITY;
            case NOT_A_NUMBER_TOKEN -> NOT_A_NUMBER;
            default -> parseNumber(token);
        };
    }

    @SuppressWarnings("NumberEquality")
    private static ProtobufToken parseNumber(String token) {
        var length = token.length();
        if(length == 0) {
            return EMPTY;
        }

        var start = 0;

        boolean isNegative;
        switch (token.charAt(start)) {
            case '+' -> {
                if(++start >= length) {
                    // Not a valid number if there are no chars available after the sign
                    return new ProtobufRawToken(token);
                }
                isNegative = false;
            }
            case '-' -> {
                if(++start >= length) {
                    // Not a valid number if there are no chars available after the sign
                    return new ProtobufRawToken(token);
                }
                isNegative = true;
            }
            default -> isNegative = false;
        }

        boolean isDecimal;
        if (token.charAt(start) == '.') {
            if(++start >= length) {
                // Not a valid number if there are no chars available after the dot
                return new ProtobufRawToken(token);
            }
            isDecimal = true;
        } else {
            isDecimal = false;
        }

        BigInteger radix;
        if (token.charAt(start) == '0' && start + 1 < length) {
            var nextChar = token.charAt(start + 1);
            if (nextChar == 'x' || nextChar == 'X') {
                radix = HEXADECIMAL_RADIX;
                start += 2;
                if(start >= length) {
                    // Not a valid hex number if there are no chars available after 0x
                    return new ProtobufRawToken(token);
                }
            } else if (nextChar >= '0' && nextChar <= '7') {
                // If there is a dot it's not an octal, it's a decimal
                var pointer = start + 1;
                while (pointer < length) {
                    if(token.charAt(pointer++) == '.') {
                        break;
                    }
                }
                if(pointer != length) { // Saves a bool
                    radix = DECIMAL_RADIX;
                }else {
                    radix = OCTAL_RADIX;
                    start++;
                }
            }else if(nextChar == '.' || nextChar == 'e' || nextChar == 'E'){
                // If there is a valid operator, it's not an invalid octal, it's a decimal
                radix = DECIMAL_RADIX;
            }else {
                // An invalid octal if the char after 0 is not a digit between 0 and 7 and not a valid operator
                return new ProtobufRawToken(token);
            }
        }else {
            radix = DECIMAL_RADIX;
        }

        var whole = BigInteger.ZERO;
        var isScientificNotation = false;
        if(!isDecimal) {
            wholeLoop: {
                char character;
                int digit;
                while (start < length) {
                    character = token.charAt(start++);
                    if (radix == OCTAL_RADIX) {
                        if (character >= '0' && character <= '7') {
                            digit = character - '0';
                        } else {
                            return new ProtobufRawToken(token);
                        }
                    } else if (radix == DECIMAL_RADIX) {
                        if (character >= '0' && character <= '9') {
                            digit = character - '0';
                        } else if (character == '.') {
                            isDecimal = true;
                            break wholeLoop;
                        } else if (character == 'e' || character == 'E') {
                            isDecimal = true;
                            isScientificNotation = true;
                            break wholeLoop;
                        } else {
                            return new ProtobufRawToken(token);
                        }
                    } else {
                        if (character >= '0' && character <= '9') {
                            digit = character - '0';
                        } else if (character >= 'a' && character <= 'f') {
                            digit = character - 'a' + 10;
                        } else if (character >= 'A' && character <= 'F') {
                            digit = character - 'A' + 10;
                        } else {
                            return new ProtobufRawToken(token);
                        }
                    }

                    whole = whole.multiply(radix)
                            .add(BigInteger.valueOf(digit));
                }
            }
        }

        var decimal = BigInteger.ZERO;
        var decimalPlaces = 0;
        if(isDecimal && !isScientificNotation) {
            char character;
            while (start < length) {
                character = token.charAt(start++);
                if (character == 'e' || character == 'E') {
                    isScientificNotation = true;
                    break;
                }

                if (character < '0' || character > '9') {
                    return new ProtobufRawToken(token);
                }

                decimal = decimal.multiply(DECIMAL_RADIX)
                        .add(BigInteger.valueOf(character - '0'));
                decimalPlaces++;
            }
        }

        var exponent = 0;
        boolean negativeExponent;
        if(isScientificNotation) {
            if (start >= length) {
                return new ProtobufRawToken(token);
            }

            switch (token.charAt(start)) {
                case '+' -> {
                    negativeExponent = false;
                    if(++start >= length) {
                        return new ProtobufRawToken(token);
                    }
                }
                case '-' -> {
                    negativeExponent = true;
                    if(++start >= length) {
                        return new ProtobufRawToken(token);
                    }
                }
                default -> negativeExponent = false;
            }

            char character;
            while (start < length) {
                character = token.charAt(start++);
                if (character < '0' || character > '9') {
                    return new ProtobufRawToken(token);
                }

                exponent = exponent * 10 + (character - '0');
            }
        }else {
            negativeExponent = false;
        }

        if (isDecimal) {
            var unscaled = radix.pow(decimalPlaces)
                    .multiply(whole)
                    .add(decimal);
            var unscaledSigned = isNegative ? unscaled.negate() : unscaled;
            if(isScientificNotation) {
                var effectiveScale = negativeExponent ? decimalPlaces + exponent : decimalPlaces - exponent;
                var scaled = new BigDecimal(unscaledSigned, effectiveScale);
                return new ProtobufNumberToken(new ProtobufFloatingPoint.Finite(scaled));
            }else {
                var scaled = new BigDecimal(unscaledSigned, decimalPlaces);
                return new ProtobufNumberToken(new ProtobufFloatingPoint.Finite(scaled));
            }
        } else {
            return new ProtobufNumberToken(new ProtobufInteger(isNegative ? whole.negate() : whole));
        }
    }

    /**
     * Returns the current line number in the input stream.
     * <p>
     * Line numbers start at 1 and are incremented for each newline character encountered.
     * This is useful for error reporting and debugging.
     * </p>
     *
     * @return the current line number
     */
    public int line() {
        return tokenizer.lineno();
    }
}
