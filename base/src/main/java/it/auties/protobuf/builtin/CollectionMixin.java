package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.annotation.ProtobufMixin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
@ProtobufMixin
public final class CollectionMixin {
    @ProtobufDefaultValue
    public static <T> Collection<T> newCollection() {
        return new ArrayList<>();
    }

    @ProtobufDefaultValue
    public static <T> SequencedCollection<T> newSequencedCollection() {
        return new ArrayList<>();
    }

    @ProtobufDefaultValue
    public static <T> List<T> newList() {
        return new ArrayList<>();
    }

    @ProtobufDefaultValue
    public static <T> Set<T> newSet() {
        return new HashSet<>();
    }

    @ProtobufDefaultValue
    public static <T> Queue<T> newQueue() {
        return new LinkedList<>();
    }

    @ProtobufDefaultValue
    public static <T> Deque<T> newDeque() {
        return new LinkedList<>();
    }

    @ProtobufDefaultValue
    public static <T> SequencedSet<T> newSequencedSet() {
        return new LinkedHashSet<>();
    }

    @ProtobufDefaultValue
    public static <T> ConcurrentHashMap.KeySetView<T, Boolean> newKeySet() {
        return ConcurrentHashMap.newKeySet();
    }
}
