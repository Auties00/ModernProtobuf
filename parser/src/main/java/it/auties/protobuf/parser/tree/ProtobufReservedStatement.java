package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.stream.Collectors;

public final class ProtobufReservedStatement
        extends ProtobufStatement
        implements ProtobufMessageChildTree, ProtobufEnumChildTree, ProtobufGroupChildTree {
    private final List<Entry<?>> entries;
    public ProtobufReservedStatement(int line) {
        super(line);
        this.entries = new ArrayList<>();
    }

    public SequencedCollection<Entry<?>> entries() {
        return Collections.unmodifiableList(entries);
    }

    public void add(Entry<?> value) {
        entries.add(value);
    }

    public void remove(Entry<?> value) {
        entries.remove(value);
    }

    public Entry<?> remove() {
        return entries.removeLast();
    }

    public EntryType type() {
        if(entries.isEmpty()) {
            return EntryType.NONE;
        }else {
            return entries.getFirst().type();
        }
    }

    @Override
    public boolean isAttributed() {
        return !entries.isEmpty()
                && entries.stream().allMatch(Entry::isValid);
    }

    public enum EntryType {
        NONE,
        FIELD_NAME,
        FIELD_INDEX
    }

    public sealed interface Entry<T> {
        SequencedCollection<T> values();
        void addValue(T value);
        boolean hasValue(T value);
        boolean isValid();
        EntryType type();

        sealed interface FieldIndex extends Entry<Integer> {
            @Override
            default EntryType type() {
                return EntryType.FIELD_INDEX;
            }

            final class Range implements FieldIndex {
                private final int min;
                private Integer max;

                public Range(int min) {
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
                public void addValue(Integer value) {
                    Objects.requireNonNull(value);
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
                public boolean hasValue(Integer entry) {
                    return entry != null
                            && entry >= min
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

            final class Values implements FieldIndex {
                private final LinkedHashSet<Integer> entries;
                public Values() {
                    this.entries = new LinkedHashSet<>();
                }

                @Override
                public SequencedCollection<Integer> values() {
                    return entries;
                }

                @Override
                public boolean hasValue(Integer value) {
                    return entries.contains(value);
                }

                @Override
                public void addValue(Integer value) {
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

        final class FieldName implements Entry<String> {
            private final LinkedHashSet<String> entries;
            public FieldName() {
                this.entries = new LinkedHashSet<>();
            }

            @Override
            public SequencedCollection<String> values() {
                return entries;
            }

            @Override
            public boolean hasValue(String value) {
                return entries.contains(value);
            }

            @Override
            public void addValue(String value) {
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
            public EntryType type() {
                return  EntryType.FIELD_NAME;
            }

            @Override
            public String toString() {
                return entries.stream().map(String::valueOf).collect(Collectors.joining(", "));
            }
        }
    }
}
