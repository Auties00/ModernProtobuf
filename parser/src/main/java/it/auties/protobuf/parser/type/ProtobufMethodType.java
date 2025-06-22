package it.auties.protobuf.parser.type;

public final class ProtobufMethodType {
    private final ProtobufTypeReference type;
    private final boolean stream;

    public static ProtobufMethodType of(ProtobufTypeReference type, boolean stream){
        return new ProtobufMethodType(type, stream);
    }

    private ProtobufMethodType(ProtobufTypeReference type, boolean stream) {
        this.type = type;
        this.stream = stream;
    }

    public ProtobufTypeReference type() {
        return type;
    }

    public boolean stream() {
        return stream;
    }
}
