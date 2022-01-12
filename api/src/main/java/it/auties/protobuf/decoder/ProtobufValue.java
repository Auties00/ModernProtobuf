package it.auties.protobuf.decoder;

record ProtobufValue(Class<?> type, boolean packed) {
    public static ProtobufValue BYTES = new ProtobufValue(byte[].class, false);
}
