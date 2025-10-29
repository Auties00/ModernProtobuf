package it.auties.protobuf.parser.token;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.parser.exception.ProtobufParserException;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;

public final class ProtobufTokenizer {
    private static final char STRING_LITERAL_DELIMITER = '"';
    private static final char STRING_LITERAL_ALIAS_DELIMITER = '\'';

    private static final String MAX_KEYWORD = "max";

    private static final String POSITIVE_INFINITY_KEYWORD = "inf";
    private static final ProtobufToken.Number.FloatingPoint POSITIVE_INFINITY = new ProtobufToken.Number.FloatingPoint(Double.POSITIVE_INFINITY);

    private static final String NEGATIVE_INFINITY_TOKEN = "-inf";
    private static final ProtobufToken.Number.FloatingPoint NEGATIVE_INFINITY = new ProtobufToken.Number.FloatingPoint(Double.NEGATIVE_INFINITY);

    private static final String NOT_A_NUMBER_TOKEN = "nan";
    private static final ProtobufToken.Number.FloatingPoint NOT_A_NUMBER = new ProtobufToken.Number.FloatingPoint(Double.NaN);

    private static final String TRUE_TOKEN = "true";
    private static final ProtobufToken.Boolean TRUE = new ProtobufToken.Boolean(true);

    private static final String FALSE_TOKEN = "false";
    private static final ProtobufToken.Boolean FALSE = new ProtobufToken.Boolean(false);

    private static final String EMPTY_TOKEN = "";
    private static final ProtobufToken EMPTY = new ProtobufToken.Raw(EMPTY_TOKEN);

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

    public String nextNullableToken() throws IOException {
        var token = tokenizer.nextToken();
        if (token == StreamTokenizer.TT_EOF) {
            return null;
        }

        return switch (token) {
            case StreamTokenizer.TT_WORD -> tokenizer.sval;
            case STRING_LITERAL_DELIMITER -> parseMultiPartString(STRING_LITERAL_DELIMITER);
            case STRING_LITERAL_ALIAS_DELIMITER -> parseMultiPartString(STRING_LITERAL_ALIAS_DELIMITER);
            default -> String.valueOf((char) token);
        };
    }

    private String parseMultiPartString(char delimiter) throws IOException {
        var result = new StringBuilder();
        result.append(delimiter);
        do {
            result.append(tokenizer.sval);
        }  while (isStringDelimiter(tokenizer.nextToken()));
        tokenizer.pushBack();
        result.append(delimiter);
        return result.toString();
    }

    private boolean isStringDelimiter(int token) {
        return token == STRING_LITERAL_DELIMITER || token == STRING_LITERAL_ALIAS_DELIMITER;
    }

    public String nextRequiredToken() throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
        }

        return token;
    }

    public String nextNullableLiteral() throws IOException {
        var token = nextNullableParsedToken();
        return token instanceof ProtobufToken.Literal(var value)
                ? value
                : null;
    }

    public String nextRequiredLiteral() throws IOException {
        var token = nextRequiredParsedToken();
        return token instanceof ProtobufToken.Literal(var value)
                ? value
                : null;
    }

    public Long nextNullablePropertyIndex(boolean enumeration, boolean allowMax) throws IOException {
        try {
            var token = nextNullableToken();
            if(token == null) {
                return null;
            }

            return parseIndex(token, enumeration, allowMax);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Long nextRequiredIndex(boolean enumeration, boolean allowMax) throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
        }

        var index = parseIndex(token, enumeration, allowMax);
        if(index == null) {
            throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
        }

        return index;
    }

    private static Long parseIndex(String token, boolean enumeration, boolean allowMax) {
        var max = enumeration ? ProtobufEnumIndex.MAX_VALUE : ProtobufProperty.MAX_INDEX;
        if(token.equalsIgnoreCase(MAX_KEYWORD)) {
            return allowMax ? max : null;
        }

        var value = 0L;
        for(var i = 0; i < token.length(); i++) {
            var charAt = token.charAt(i);
            if (charAt < '0' || charAt > '9') {
                return null;
            }

            var valueTimesTen = value * 10L;
            if (((value | 10L) >>> 31 != 0) && valueTimesTen / 10 != value) {
                return null;
            }

            var digit = token.charAt(i) - '0';
            var r = valueTimesTen + digit;
            if (((valueTimesTen ^ r) & (digit ^ r)) < 0) {
                return null;
            }

            value = r;
        }

        var min = enumeration ? ProtobufEnumIndex.MIN_VALUE : ProtobufProperty.MIN_INDEX;
        if(value < min || value > max) {
            return null;
        }

        return value;
    }


    public ProtobufToken nextNullableParsedToken() throws IOException {
        var token = tokenizer.nextToken();
        return switch (token) {
            case StreamTokenizer.TT_EOL -> null;
            case StreamTokenizer.TT_WORD -> parseToken(tokenizer.sval);
            case STRING_LITERAL_DELIMITER, STRING_LITERAL_ALIAS_DELIMITER -> parseMultiPartString(tokenizer.sval);
            default -> new ProtobufToken.Raw(String.valueOf((char) token));
        };
    }

    public ProtobufToken nextRequiredParsedToken() throws IOException {
        var token = nextNullableParsedToken();
        if(token == null) {
            throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
        }

        return token;
    }

    private ProtobufToken parseMultiPartString(String head) throws IOException {
        var token = tokenizer.nextToken();
        if(!isStringDelimiter(token)) {
            tokenizer.pushBack();
            return new ProtobufToken.Literal(head);
        }else {
            var result = new StringBuilder();
            result.append(head);
            do {
                result.append(tokenizer.sval);
            } while (isStringDelimiter((tokenizer.nextToken())));
            tokenizer.pushBack();
            return new ProtobufToken.Literal(result.toString());
        }
    }

    // FIXME: Not ready
    private static ProtobufToken parseToken(String token) {
        return switch (token) {
            case EMPTY_TOKEN -> EMPTY;
            case TRUE_TOKEN -> TRUE;
            case FALSE_TOKEN -> FALSE;
            case POSITIVE_INFINITY_KEYWORD -> POSITIVE_INFINITY;
            case NEGATIVE_INFINITY_TOKEN -> NEGATIVE_INFINITY;
            case NOT_A_NUMBER_TOKEN -> NOT_A_NUMBER;
            default -> {
                var length = token.length();

                int start;
                boolean decimal;
                boolean negative;
                switch (token.charAt(0)) {
                    case '+' -> {
                        start = 1;
                        decimal = false;
                        negative = false;
                    }
                    case '-' -> {
                        start = 1;
                        decimal = false;
                        negative = true;
                    }
                    case '.' -> {
                        start = 1;
                        decimal = true;
                        negative = false;
                    }
                    default -> {
                        start = 0;
                        decimal = false;
                        negative = false;
                    }
                }

                int radix;
                if (start < length && token.charAt(start) == '0' && start + 1 < length) {
                    char nextChar = token.charAt(start + 1);
                    if (nextChar == 'x' || nextChar == 'X') {
                        radix = 16;
                        start += 2;
                    } else if (nextChar >= '0' && nextChar <= '7') {
                        radix = 8;
                        start += 1;
                    }else {
                        radix = 10;
                    }
                }else {
                    radix = 10;
                }

                var whole = 0L;
                var scientificNotation = false;
                if(!decimal) {
                    wholeLoop: {
                        char character;
                        int digit;
                        while (start < length) {
                            character = token.charAt(start++);
                            switch (radix) {
                                case 8 -> {
                                    if (character >= '0' && character <= '7') {
                                        digit = character - '0';
                                    } else {
                                        yield new ProtobufToken.Raw(token);
                                    }
                                }
                                case 10 -> {
                                    if (character >= '0' && character <= '9') {
                                        digit = character - '0';
                                    }else if(character == '.') {
                                        decimal = true;
                                        break wholeLoop;
                                    } else if(character == 'e' || character == 'E') {
                                        decimal = true;
                                        scientificNotation = true;
                                        break wholeLoop;
                                    } else {
                                        yield new ProtobufToken.Raw(token);
                                    }
                                }
                                case 16 -> {
                                    if (character >= '0' && character <= '9') {
                                        digit = character - '0';
                                    } else if (character >= 'a' && character <= 'f') {
                                        digit = character - 'a' + 10;
                                    } else if (character >= 'A' && character <= 'F') {
                                        digit = character - 'A' + 10;
                                    } else {
                                        yield new ProtobufToken.Raw(token);
                                    }
                                }
                                default -> throw new InternalError("Unexpected radix " + radix);
                            }

                            var valueTimesRadix = whole * radix;
                            if (((whole | radix) >>> 31 != 0) && valueTimesRadix / radix != whole) {
                                yield new ProtobufToken.Raw(token);
                            }

                            whole = valueTimesRadix + digit;
                            if (((valueTimesRadix ^ whole) & (digit ^ whole)) < 0) {
                                yield new ProtobufToken.Raw(token);
                            }
                        }
                    }
                }

                var fraction = 0L;
                var divisor = 1.0;
                var divisorDigits = 0;
                if(!scientificNotation) {
                    char character;
                    while (start < length) {
                        character = token.charAt(start++);
                        if (character == 'e' || character == 'E') {
                            scientificNotation = true;
                            break;
                        }

                        if (character < '0' || character > '9') {
                            yield new ProtobufToken.Raw(token);
                        }

                        var valueTimesTen = fraction * 10L;
                        if (((fraction | 10L) >>> 31 != 0) && valueTimesTen / 10 != fraction) {
                            yield new ProtobufToken.Raw(token);
                        }

                        var digit = character - '0';
                        fraction = valueTimesTen + digit;
                        if (((valueTimesTen ^ fraction) & (digit ^ fraction)) < 0) {
                            yield new ProtobufToken.Raw(token);
                        }

                        divisor *= 10.0;
                        divisorDigits++;
                    }
                }

                var exponent = 0;
                boolean negativeExponent;
                if(scientificNotation) {
                    if (start >= length) {
                        yield new ProtobufToken.Raw(token);
                    }

                    switch (token.charAt(start)) {
                        case '+' -> {
                            negativeExponent = false;
                            if(++start >= length) {
                                yield new ProtobufToken.Raw(token);
                            }
                        }
                        case '-' -> {
                            negativeExponent = true;
                            if(++start >= length) {
                                yield new ProtobufToken.Raw(token);
                            }
                        }
                        default -> negativeExponent = false;
                    }

                    while (start < length) {
                        char charAt = token.charAt(start++);
                        if (charAt < '0' || charAt > '9') {
                            yield new ProtobufToken.Raw(token);
                        }

                        exponent = exponent * 10 + (charAt - '0');
                        if (exponent > Double.MAX_EXPONENT) {
                            yield new ProtobufToken.Raw(token);
                        }
                    }
                }else {
                    negativeExponent = false;
                }

                if (!decimal) {
                    yield new ProtobufToken.Number.Integer(negative ? -whole : whole);
                } else {
                    double decimalValue;
                    if (!scientificNotation) {
                        decimalValue = whole + (fraction / divisor);
                    } else {
                        decimalValue = whole * divisor + fraction;
                        if (negativeExponent) {
                            decimalValue /= Math.powExact(10L, exponent + divisorDigits);
                        } else {
                            decimalValue *= Math.powExact(10L, exponent - divisorDigits);
                        }
                    }
                    yield new ProtobufToken.Number.FloatingPoint(negative ? -decimalValue : decimalValue);
                }
            }
        };
    }

    public int line() {
        return tokenizer.lineno();
    }
}
