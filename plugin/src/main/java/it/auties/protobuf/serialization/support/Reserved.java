package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufReservedRange;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.object.ProtobufReservedIndex;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Reserved {
    public static Set<String> getNames(ProtobufObjectElement objectElement) {
        return switch (objectElement.type()) {
            case MESSAGE -> {
                var message = objectElement.element().getAnnotation(ProtobufMessage.class);
                yield  message == null ? Set.of() : Set.of(message.reservedNames());
            }
            case ENUM -> {
                var enumeration = objectElement.element().getAnnotation(ProtobufEnum.class);
                yield enumeration == null ? Set.of() : Set.of(enumeration.reservedNames());
            }
            case GROUP -> {
                var group = objectElement.element().getAnnotation(ProtobufGroup.class);
                yield group == null ? Set.of() : Set.of(group.reservedNames());
            }
            case SYNTHETIC -> Set.of();
        };
    }

    public static Set<ProtobufReservedIndex> getIndexes(ProtobufObjectElement objectElement) {
        return switch (objectElement.type()) {
            case MESSAGE -> {
                var message = objectElement.element().getAnnotation(ProtobufMessage.class);
                yield getReservedIndexes(message.reservedIndexes(), message.reservedRanges());
            }
            case ENUM -> {
                var enumeration = objectElement.element().getAnnotation(ProtobufEnum.class);
                yield getReservedIndexes(enumeration.reservedIndexes(), enumeration.reservedRanges());
            }
            case GROUP -> {
                var group = objectElement.element().getAnnotation(ProtobufGroup.class);
                yield getReservedIndexes(group.reservedIndexes(), group.reservedRanges());
            }
            case SYNTHETIC -> Set.of();
        };
    }

    private static Set<ProtobufReservedIndex> getReservedIndexes(int[] indexes, ProtobufReservedRange[] ranges) {
        var results = new HashSet<ProtobufReservedIndex>();
        for(var index : indexes) {
            results.add(new ProtobufReservedIndex.Value(index));
        }

        for(var range : ranges) {
            results.add(new ProtobufReservedIndex.Range(range.min(), range.max()));
        }

        return Collections.unmodifiableSet(results);
    }
}
