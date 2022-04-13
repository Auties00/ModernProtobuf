package it.auties.protobuf.parser.model;

import java.util.List;

public class ProtobufSyntaxException extends IllegalArgumentException {
    public ProtobufSyntaxException(String message, List<String> tokens, Object... args) {
        super("%s(%s)".formatted(formatMessage(message, args), formatTokens(tokens)));
    }

    private static String formatTokens(List<String> tokens) {
        return String.join(" ", tokens);
    }

    private static Object formatMessage(String message, Object[] args) {
        return message == null ? null : message.formatted(args);
    }

    public static void check(boolean condition, String message, List<String> tokens, Object... args){
        if(condition){
            return;
        }

        throw new ProtobufSyntaxException(message, tokens, args);
    }
}
