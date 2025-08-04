package it.auties.protobuf.serialization.model;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufReservedRange;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;

public sealed interface ProtobufReservedElement {
    static SequencedSet<? extends ProtobufReservedElement> of(ProtobufGroup groupAnnotation) {
        Objects.requireNonNull(groupAnnotation, "Annotation cannot be null");
        return collect(
                groupAnnotation.reservedIndexes(),
                groupAnnotation.reservedNames(),
                groupAnnotation.reservedRanges()
        );
    }

    static SequencedSet<? extends ProtobufReservedElement> of(ProtobufMessage messageAnnotation) {
        Objects.requireNonNull(messageAnnotation, "Annotation cannot be null");
        return collect(
                messageAnnotation.reservedIndexes(),
                messageAnnotation.reservedNames(),
                messageAnnotation.reservedRanges()
        );
    }

    static SequencedSet<? extends ProtobufReservedElement> of(ProtobufEnum enumAnnotation) {
        Objects.requireNonNull(enumAnnotation, "Annotation cannot be null");
        return collect(
                enumAnnotation.reservedIndexes(),
                enumAnnotation.reservedNames(),
                enumAnnotation.reservedRanges()
        );
    }

    private static SequencedSet<ProtobufReservedElement> collect(int[] groupAnnotation, String[] groupAnnotation1, ProtobufReservedRange[] groupAnnotation2) {
        var reserved = new LinkedHashSet<ProtobufReservedElement>();
        for (var reservedIndex : groupAnnotation) {
            reserved.add(new Index.Value(reservedIndex));
        }
        for (var reservedName : groupAnnotation1) {
            reserved.add(new Name(reservedName));
        }
        for (var reservedRange : groupAnnotation2) {
            reserved.add(new Index.Range(reservedRange.min(), reservedRange.max()));
        }
        return Collections.unmodifiableSequencedSet(reserved);
    }

    record Name(String name) implements ProtobufReservedElement {
        public boolean allows(String name) {
            return !this.name.equals(name);
        }
    }

    sealed interface Index extends ProtobufReservedElement {
        boolean allows(long index);

        record Range(long min, long max) implements Index {
            @Override
            public boolean allows(long index) {
                return index < min || index > max;
            }
        }

        record Value(long value) implements Index {
            @Override
            public boolean allows(long index) {
                return index != value;
            }
        }
    }
}
