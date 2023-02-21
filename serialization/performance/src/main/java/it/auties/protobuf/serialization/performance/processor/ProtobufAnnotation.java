package it.auties.protobuf.serialization.performance.processor;

import it.auties.protobuf.serialization.performance.model.ProtobufEntry;
import it.auties.protobuf.serialization.performance.model.ProtobufWritable;

public class ProtobufAnnotation {
    public static int getFlags(ProtobufWritable entry) {
        return getFlags(entry.required(), entry.ignore(), entry.packed(), entry.repeated());
    }

    private static int getFlags(boolean required, boolean ignore, boolean packed, boolean repeated) {
        var flags = 0;
        if (required) {
            flags |= ProtobufEntry.REQUIRED_MODIFIER;
        }

        if (ignore) {
            flags |= ProtobufEntry.IGNORE_MODIFIER;
        }

        if (packed) {
            flags |= ProtobufEntry.PACKED_MODIFIER;
        }

        if (repeated) {
            flags |= ProtobufEntry.REPEATED_MODIFIER;
        }

        return flags;
    }

    public static ProtobufEntry toNonRepeatedField(ProtobufEntry property){
        var modifiers = property.modifiers();
        modifiers &= ~ProtobufEntry.REPEATED_MODIFIER;
        return new ProtobufEntry(property.index(), property.type(), property.implementation(), modifiers);
    }
}
