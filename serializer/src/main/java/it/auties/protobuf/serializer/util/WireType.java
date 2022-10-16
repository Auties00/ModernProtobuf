package it.auties.protobuf.serializer.util;

public class WireType {
    public final static int WIRE_TYPE_VAR_INT = 0;
    public final static int WIRE_TYPE_FIXED64 = 1;
    public final static int WIRE_TYPE_LENGTH_DELIMITED = 2;
    public final static int WIRE_TYPE_EMBEDDED_MESSAGE = 3;
    public final static int WIRE_TYPE_END_OBJECT = 4;
    public final static int WIRE_TYPE_FIXED32 = 5;
}
