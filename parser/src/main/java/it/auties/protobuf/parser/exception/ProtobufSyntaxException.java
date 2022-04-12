package it.auties.protobuf.parser.exception;

import java.util.List;

public class ProtobufSyntaxException extends IllegalArgumentException {
    public ProtobufSyntaxException(String message, List<String> tokens, Object... args) {
        super("%s(%s)".formatted(message == null ? null : message.formatted(args), tokens));
    }

    public static void validate(boolean condition, String message, List<String> tokens, Object... args){
        if(condition){
            return;
        }

        throw new ProtobufSyntaxException(message, tokens, args);
    }
}
