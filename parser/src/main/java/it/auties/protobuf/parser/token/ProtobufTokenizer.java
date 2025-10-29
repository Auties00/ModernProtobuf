package it.auties.protobuf.parser.token;

import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.type.ProtobufFloatingPoint;
import it.auties.protobuf.parser.type.ProtobufFloatingPoint.Infinity.Signum;
import it.auties.protobuf.parser.type.ProtobufInteger;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class ProtobufTokenizer {
    private static final char STRING_LITERAL_DELIMITER = '"';
    private static final char STRING_LITERAL_ALIAS_DELIMITER = '\'';

    private static final String POSITIVE_INFINITY_KEYWORD = "inf";
    private static final ProtobufToken POSITIVE_INFINITY = new ProtobufToken.Number(new ProtobufFloatingPoint.Infinity(Signum.POSITIVE));

    private static final String NEGATIVE_INFINITY_TOKEN = "-inf";
    private static final ProtobufToken NEGATIVE_INFINITY = new ProtobufToken.Number(new ProtobufFloatingPoint.Infinity(Signum.NEGATIVE));

    private static final String NOT_A_NUMBER_TOKEN = "nan";
    private static final ProtobufToken NOT_A_NUMBER = new ProtobufToken.Number(new ProtobufFloatingPoint.NaN());

    private static final String TRUE_TOKEN = "true";
    private static final ProtobufToken TRUE = new ProtobufToken.Boolean(true);

    private static final String FALSE_TOKEN = "false";
    private static final ProtobufToken FALSE = new ProtobufToken.Boolean(false);

    private static final String EMPTY_TOKEN = "";
    private static final ProtobufToken EMPTY = new ProtobufToken.Raw(EMPTY_TOKEN);

    private static final BigInteger OCTAL_RADIX = BigInteger.valueOf(8);
    private static final BigInteger DECIMAL_RADIX = BigInteger.valueOf(10);
    private static final BigInteger HEXADECIMAL_RADIX = BigInteger.valueOf(16);

    private final StreamTokenizer tokenizer;

    public ProtobufTokenizer(Reader reader) {
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
        for (int i = '0'; i <= '9'; i++) {
            tokenizer.wordChars(i, i);
        }
        tokenizer.whitespaceChars(0, ' ');
        tokenizer.slashSlashComments(true);
        tokenizer.slashStarComments(true);
        tokenizer.quoteChar(STRING_LITERAL_DELIMITER);
        tokenizer.quoteChar(STRING_LITERAL_ALIAS_DELIMITER);
    }

    public String nextRawToken() throws IOException {
        return nextRawToken(true);
    }

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
            tokenizer.pushBack();
            return head;
        }else {
            var result = new StringBuilder();
            result.append(head);
            do {
                result.append(tokenizer.sval);
            } while (isStringDelimiter((tokenizer.nextToken())));
            tokenizer.pushBack();
            return result.toString();
        }
    }

    private boolean isStringDelimiter(int token) {
        return token == STRING_LITERAL_DELIMITER || token == STRING_LITERAL_ALIAS_DELIMITER;
    }

    public ProtobufToken nextToken() throws IOException {
        var token = tokenizer.nextToken();
        return switch (token) {
            case StreamTokenizer.TT_EOL -> throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
            case StreamTokenizer.TT_WORD -> parseWord(tokenizer.sval);
            case STRING_LITERAL_DELIMITER -> new ProtobufToken.Literal(parseMultiPartStringToken(tokenizer.sval), STRING_LITERAL_DELIMITER);
            case STRING_LITERAL_ALIAS_DELIMITER -> new ProtobufToken.Literal(parseMultiPartStringToken(tokenizer.sval), STRING_LITERAL_ALIAS_DELIMITER);
            default -> new ProtobufToken.Raw(String.valueOf((char) token));
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
            default -> parseBigNumber(token);
        };
    }

    @SuppressWarnings("NumberEquality")
    private static ProtobufToken parseBigNumber(String token) {
        var length = token.length();

        int start;
        boolean isDecimal;
        boolean isNegative;
        switch (token.charAt(0)) {
            case '+' -> {
                start = 1;
                isDecimal = false;
                isNegative = false;
            }
            case '-' -> {
                start = 1;
                isDecimal = false;
                isNegative = true;
            }
            case '.' -> {
                start = 1;
                isDecimal = true;
                isNegative = false;
            }
            default -> {
                start = 0;
                isDecimal = false;
                isNegative = false;
            }
        }

        BigInteger radix;
        if (start < length && token.charAt(start) == '0' && start + 1 < length) {
            char nextChar = token.charAt(start + 1);
            if (nextChar == 'x' || nextChar == 'X') {
                radix = HEXADECIMAL_RADIX;
                start += 2;
            } else if (nextChar >= '0' && nextChar <= '7') {
                radix = OCTAL_RADIX;
                start += 1;
            }else {
                radix = DECIMAL_RADIX;
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
                            return new ProtobufToken.Raw(token);
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
                            return new ProtobufToken.Raw(token);
                        }
                    } else {
                        if (character >= '0' && character <= '9') {
                            digit = character - '0';
                        } else if (character >= 'a' && character <= 'f') {
                            digit = character - 'a' + 10;
                        } else if (character >= 'A' && character <= 'F') {
                            digit = character - 'A' + 10;
                        } else {
                            return new ProtobufToken.Raw(token);
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
                    return new ProtobufToken.Raw(token);
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
                return new ProtobufToken.Raw(token);
            }

            switch (token.charAt(start)) {
                case '+' -> {
                    negativeExponent = false;
                    if(++start >= length) {
                        return new ProtobufToken.Raw(token);
                    }
                }
                case '-' -> {
                    negativeExponent = true;
                    if(++start >= length) {
                        return new ProtobufToken.Raw(token);
                    }
                }
                default -> negativeExponent = false;
            }

            char character;
            while (start < length) {
                character = token.charAt(start++);
                if (character < '0' || character > '9') {
                    return new ProtobufToken.Raw(token);
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
                return new ProtobufToken.Number(new ProtobufFloatingPoint.Finite(scaled));
            }else {
                var scaled = new BigDecimal(unscaledSigned, decimalPlaces);
                return new ProtobufToken.Number(new ProtobufFloatingPoint.Finite(scaled));
            }
        } else {
            return new ProtobufToken.Number(new ProtobufInteger(isNegative ? whole.negate() : whole));
        }
    }

    public int line() {
        return tokenizer.lineno();
    }
}
