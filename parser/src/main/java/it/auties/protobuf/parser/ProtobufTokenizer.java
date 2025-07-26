package it.auties.protobuf.parser;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;

public final class ProtobufTokenizer {
    private static final char STRING_LITERAL_DELIMITER = '"';
    private static final String STRING_LITERAL = "\"";
    private static final String STRING_LITERAL_ALIAS_CHAR = "'";
    private static final String MAX_KEYWORD = "max";

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
        tokenizer.commentChar('/');
        tokenizer.quoteChar(STRING_LITERAL_DELIMITER);
    }

    public String nextNullableToken() throws IOException {
        var token = tokenizer.nextToken();
        if (token == StreamTokenizer.TT_EOF) {
            return null;
        }

        return switch (token) {
            case StreamTokenizer.TT_WORD -> tokenizer.sval;
            case STRING_LITERAL_DELIMITER -> STRING_LITERAL + tokenizer.sval + STRING_LITERAL;
            case StreamTokenizer.TT_NUMBER -> String.valueOf((int) tokenizer.nval);
            default -> String.valueOf((char) token);
        };
    }

    public String nextRequiredToken() throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
        }

        return token;
    }

    public String nextNullableLiteral() throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            return null;
        }

        return parseLiteral(token);
    }

    public String nextRequiredLiteral() throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
        }

        var literal = parseLiteral(token);
        if(literal == null) {
            throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
        }

        return literal;
    }

    private static String parseLiteral(String token) {
        if ((!token.startsWith(STRING_LITERAL) || !token.endsWith(STRING_LITERAL))
                && (!token.startsWith(STRING_LITERAL_ALIAS_CHAR) || !token.endsWith(STRING_LITERAL_ALIAS_CHAR))) {
            return null;
        }

        return token.substring(1, token.length() - 1);
    }

    public Long nextNullableIndex(boolean enumeration, boolean allowMax) throws IOException {
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

    private static Boolean parseBool(String token) {
        return switch (token) {
            case "true" -> true;
            case "false" -> false;
            default -> null;
        };
    }


    public ParsedToken nextNullableParsedToken() throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            return null;
        }

        return parseToken(token);
    }

    public ParsedToken nextRequiredParsedToken() throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
        }

        return parseToken(token);
    }

    private static ParsedToken parseToken(String token) {
        var literal = parseLiteral(token);
        if(literal != null) {
            return new ParsedToken.Literal(literal);
        }

        var number = parseTokenAsNumber(token);
        if(number != null) {
            return number;
        }

        var bool = parseBool(token);
        if(bool != null) {
            return new ParsedToken.Boolean(bool);
        }

        return new ParsedToken.Raw(token);
    }

    private static ParsedToken parseTokenAsNumber(String token) {
        var length = token.length();
        if(length == 0) {
            return null;
        }

        int start;
        boolean negative;
        switch (token.charAt(0)) {
            case '+' -> {
                start = 1;
                negative = false;
            }
            case '-' -> {
                start = 1;
                negative = true;
            }
            default -> {
                start = 0;
                negative = false;
            }
        }

        var whole = 0L;
        while (start < length) {
            var charAt = token.charAt(start++);
            if(charAt == '.') {
                break;
            }else if (charAt < '0' || charAt > '9') {
                return null;
            }

            var valueTimesTen = whole * 10L;
            if (((whole | 10L) >>> 31 != 0) && valueTimesTen / 10 != whole) {
                return null;
            }

            var digit = charAt - '0';
            whole = valueTimesTen + digit;
            if (((valueTimesTen ^ whole) & (digit ^ whole)) < 0) {
                return null;
            }
        }

        var fraction = 0L;
        while (start < length) {
            var charAt = token.charAt(start++);
            if (charAt < '0' || charAt > '9') {
                return null;
            }

            var valueTimesTen = fraction * 10L;
            if (((fraction | 10L) >>> 31 != 0) && valueTimesTen / 10 != fraction) {
                return null;
            }

            var digit = charAt - '0';
            fraction = valueTimesTen + digit;
            if (((valueTimesTen ^ fraction) & (digit ^ fraction)) < 0) {
                return null;
            }
        }

        if(fraction == 0) {
            return new ParsedToken.Number.Integer(negative ? -whole : whole);
        }else {
            // TODO
            throw new UnsupportedOperationException();
        }
    }

    public int line() {
        return tokenizer.lineno();
    }

    public sealed interface ParsedToken {
        record Literal(String value) implements ParsedToken{

        }

        sealed interface Number extends ParsedToken {
            boolean isValidPropertyIndex();
            boolean isValidEnumConstantIndex();

            record Integer(long value) implements Number {
                @Override
                public boolean isValidPropertyIndex() {
                    return value >= ProtobufProperty.MIN_INDEX && value <= ProtobufProperty.MAX_INDEX;
                }

                @Override
                public boolean isValidEnumConstantIndex() {
                    return value >= ProtobufEnumIndex.MIN_VALUE && value <= ProtobufEnumIndex.MAX_VALUE;
                }
            }

            record FloatingPoint(double value) implements Number {
                @Override
                public boolean isValidPropertyIndex() {
                    return false;
                }

                @Override
                public boolean isValidEnumConstantIndex() {
                    return false;
                }
            }
        }

        record Boolean(boolean value) implements ParsedToken {

        }

        record Raw(String value) implements ParsedToken {

        }
    }
}
