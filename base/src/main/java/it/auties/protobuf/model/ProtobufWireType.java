package it.auties.protobuf.model;

public final class ProtobufWireType {
    public final static int WIRE_TYPE_VAR_INT = 0;
    public final static int WIRE_TYPE_FIXED64 = 1;
    public final static int WIRE_TYPE_LENGTH_DELIMITED = 2;
    public final static int WIRE_TYPE_START_OBJECT = 3;
    public final static int WIRE_TYPE_END_OBJECT = 4;
    public final static int WIRE_TYPE_FIXED32 = 5;

    public static int makeTag(int fieldNumber, int wireType) {
        return (fieldNumber << 3) | wireType;
    }
}
