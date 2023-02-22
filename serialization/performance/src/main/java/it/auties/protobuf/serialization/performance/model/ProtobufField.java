package it.auties.protobuf.serialization.performance.model;

import it.auties.protobuf.base.ProtobufType;

public record ProtobufField(int index, ProtobufType type, Class<?> implementation, int modifiers) {
    public static final int REQUIRED_MODIFIER = 0x00000001;
    public static final int IGNORE_MODIFIER = 0x00000002;
    public static final int PACKED_MODIFIER = 0x00000004;
    public static final int REPEATED_MODIFIER = 0x00000008;

    public boolean required(){
        return (modifiers & REQUIRED_MODIFIER) != 0;
    }

    public boolean ignore(){
        return (modifiers & IGNORE_MODIFIER) != 0;
    }

    public boolean packed(){
        return (modifiers & PACKED_MODIFIER) != 0;
    }

    public boolean repeated(){
        return (modifiers & REPEATED_MODIFIER) != 0;
    }
}