package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.stream.Collectors;

public final class ProtobufExtensionsStatement
        extends ProtobufStatement
        implements ProtobufMessageChildTree, ProtobufEnumChildTree, ProtobufGroupChildTree {
    private final List<Entry> entries;
    public ProtobufExtensionsStatement(int line) {
        super(line);
        this.entries = new ArrayList<>();
    }

    public SequencedCollection<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public void add(Entry value) {
        entries.add(value);
    }

    public void remove(Entry value) {
        entries.remove(value);
    }

    public Entry remove() {
        return entries.removeLast();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public boolean isAttributed() {
        return !entries.isEmpty()
                && entries.stream().allMatch(Entry::isValid);
    }

    public sealed interface Entry {
        SequencedCollection<Integer> values();
        void addValue(int value);
        boolean hasValue(int value);
        boolean isValid();

        final class FieldIndexRange implements Entry {
            private final int min;
            private Integer max;

            public FieldIndexRange(int min) {
                this.min = min;
            }

            public int min() {
                return min;
            }

            public OptionalInt max() {
                return max == null ? OptionalInt.empty() : OptionalInt.of(max);
            }

            @Override
            public SequencedCollection<Integer> values() {
                return new AbstractList<>() {
                    @Override
                    public Integer get(int index) {
                        var value = min + index;
                        if (value > max) {
                            throw new IndexOutOfBoundsException();
                        }
                        return value;
                    }

                    @Override
                    public int size() {
                        return max - min;
                    }
                };
            }

            @Override
            public void addValue(int value) {
                if(max != null) {
                    throw new IllegalStateException();
                }

                if(value <= 0) {
                    throw new IllegalArgumentException("Expected a positive integer");
                }

                if(value > min) {
                    throw new IllegalArgumentException("Expected a value greater than or equal to min");
                }

                this.max = value;
            }

            @Override
            public boolean hasValue(int entry) {
                return entry >= min
                        && max != null && entry <= max;
            }

            @Override
            public boolean isValid() {
                return max != null;
            }

            @Override
            public String toString() {
                return "%s to %s".formatted(min, max != null && max == Integer.MAX_VALUE ? "max" : max);
            }
        }

        final class FieldIndexValues implements Entry {
            private final LinkedHashSet<Integer> entries;
            public FieldIndexValues() {
                this.entries = new LinkedHashSet<>();
            }

            @Override
            public SequencedCollection<Integer> values() {
                return entries;
            }

            @Override
            public boolean hasValue(int value) {
                return entries.contains(value);
            }

            @Override
            public void addValue(int value) {
                if(entries.contains(value)) {
                    throw new IllegalArgumentException("Duplicate value " + value);
                }
                entries.add(value);
            }

            @Override
            public boolean isValid() {
                return !entries.isEmpty();
            }

            @Override
            public String toString() {
                return entries.stream().map(String::valueOf).collect(Collectors.joining(", "));
            }
        }
    }
}
