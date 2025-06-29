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

    public String nextToken() throws IOException {
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

    public String nextString(String token) {
        if ((token.startsWith(STRING_LITERAL) && token.endsWith(STRING_LITERAL)) || (token.startsWith(STRING_LITERAL_ALIAS_CHAR) && token.endsWith(STRING_LITERAL_ALIAS_CHAR))) {
            return token.substring(1, token.length() - 1);
        } else {
            return null;
        }
    }

    public Integer nextInt(boolean allowMax) throws IOException {
        try {
            var token = nextToken();
            if(token == null) {
                return null;
            }

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
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Boolean nextBool() throws IOException {
        return switch (nextToken()) {
            case "true" -> true;
            case "false" -> false;
            default -> null;
        };
    }

    public int line() {
        return tokenizer.lineno();
    }
}
