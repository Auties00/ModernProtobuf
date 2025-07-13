package it.auties.protobuf.parser;

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

        return parseLiteral(token);
    }

    private static String parseLiteral(String token) {
        if ((!token.startsWith(STRING_LITERAL) || !token.endsWith(STRING_LITERAL))
                && (!token.startsWith(STRING_LITERAL_ALIAS_CHAR) || !token.endsWith(STRING_LITERAL_ALIAS_CHAR))) {
            return null;
        }

        return token.substring(1, token.length() - 1);
    }

    public Integer nextNullableInt(boolean allowMax) throws IOException {
        try {
            var token = nextNullableToken();
            if(token == null) {
                return null;
            }

            return parseInt(token, allowMax);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Integer nextRequiredInt(boolean allowMax) throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
        }

        return parseInt(token, allowMax);
    }

    private static Integer parseInt(String token, boolean allowMax) {
        if(token.equalsIgnoreCase(MAX_KEYWORD)) {
            return allowMax ? Integer.MAX_VALUE : null;
        }

        var value = 0;
        for(var i = 0; i < token.length(); i++) {
            var c = token.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
            value *= 10;
            value += token.charAt(i) - '0';
        }
        return value;
    }

    public Boolean nextNullableBool() throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            return null;
        }

        return parseBool(token);
    }

    public Boolean nextRequiredBool() throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
        }

        return parseBool(token);
    }

    private static Boolean parseBool(String token) {
        return switch (token) {
            case "true" -> true;
            case "false" -> false;
            default -> null;
        };
    }

    public ParsedToken nextNullableParsedToken(boolean allowMax) throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            return null;
        }

        return parseToken(token, allowMax);
    }

    public ParsedToken nextRequiredParsedToken(boolean allowMax) throws IOException {
        var token = nextNullableToken();
        if(token == null) {
            throw new ProtobufParserException("Unexpected end of input", tokenizer.lineno());
        }

        return parseToken(token, allowMax);
    }

    private static ParsedToken parseToken(String token, boolean allowMax) {
        var literal = parseLiteral(token);
        if(literal != null) {
            return new ParsedToken.Literal(literal);
        }

        var integer = parseInt(token, allowMax);
        if(integer != null) {
            return new ParsedToken.Int(integer);
        }

        var bool = parseBool(token);
        if(bool != null) {
            return new ParsedToken.Bool(bool);
        }

        return new ParsedToken.Raw(token);
    }

    public int line() {
        return tokenizer.lineno();
    }

    public sealed interface ParsedToken {
        record Literal(String value) implements ParsedToken{

        }

        record Int(int value) implements ParsedToken {

        }

        record Bool(boolean value) implements ParsedToken {

        }

        record Raw(String value) implements ParsedToken {

        }
    }
}
