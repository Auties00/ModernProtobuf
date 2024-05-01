package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.model.ProtobufMixin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ProtobufMapMixin implements ProtobufMixin {
    @ProtobufDefaultValue
    public static <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }

    @ProtobufDefaultValue
    public static <K, V> ConcurrentMap<K, V> newConcurrentMap() {
        return new ConcurrentHashMap<>();
    }

    @ProtobufDefaultValue
    public static <K, V> SequencedMap<K, V> newSequencedMap() {
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("SortedCollectionWithNonComparableKeys")
    @ProtobufDefaultValue
    public static <K, V> NavigableMap<K, V> newNavigableMap() {
        return new TreeMap<>();
    }

    @SuppressWarnings("SortedCollectionWithNonComparableKeys")
    @ProtobufDefaultValue
    public static <K, V> SortedMap<K, V> newSortedMap() {
        return new TreeMap<>();
    }
}
