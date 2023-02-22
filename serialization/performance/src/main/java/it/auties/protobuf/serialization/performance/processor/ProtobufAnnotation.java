package it.auties.protobuf.serialization.performance.processor;

import it.auties.protobuf.serialization.performance.model.ProtobufField;
import it.auties.protobuf.serialization.performance.model.ProtobufWritable;

public class ProtobufAnnotation {
    public static int getFlags(ProtobufWritable entry) {
        return getFlags(entry.required(), entry.ignore(), entry.packed(), entry.repeated());
    }

    private static int getFlags(boolean required, boolean ignore, boolean packed, boolean repeated) {
        var flags = 0;
        if (required) {
            flags |= ProtobufField.REQUIRED_MODIFIER;
        }

        if (ignore) {
            flags |= ProtobufField.IGNORE_MODIFIER;
        }

        if (packed) {
            flags |= ProtobufField.PACKED_MODIFIER;
        }

        if (repeated) {
            flags |= ProtobufField.REPEATED_MODIFIER;
        }

        return flags;
    }

    public static ProtobufField toNonRepeatedField(ProtobufField property){
        var modifiers = property.modifiers();
        modifiers &= ~ProtobufField.REPEATED_MODIFIER;
        return new ProtobufField(property.index(), property.type(), property.implementation(), modifiers);
    }
}
