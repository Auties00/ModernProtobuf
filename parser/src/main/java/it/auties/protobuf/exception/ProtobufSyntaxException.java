package it.auties.protobuf.exception;

public class ProtobufSyntaxException extends IllegalArgumentException {
    public ProtobufSyntaxException(String message, Object... args) {
        super(message == null ? null : message.formatted(args));
    }

    public static void validate(boolean condition, String message, Object... args){
        if(condition){
            return;
        }

        throw new ProtobufSyntaxException(message, args);
    }
}
