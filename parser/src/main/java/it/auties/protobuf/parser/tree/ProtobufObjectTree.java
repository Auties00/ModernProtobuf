package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.exception.ProtobufInternalException;

import java.util.*;
import java.util.stream.Collectors;

public sealed class ProtobufObjectTree<T extends ProtobufTree> extends ProtobufIndexedBodyTree<T> permits ProtobufEnumTree, ProtobufMessageTree {
    private final LinkedList<Reserved> reserved;
    private final LinkedList<Extensions> extensions;

    ProtobufObjectTree(String name) {
        super(name);
        this.reserved = new LinkedList<>();
        this.extensions = new LinkedList<>();
    }

    public List<Reserved> reserved() {
        return Collections.unmodifiableList(reserved);
    }

    public Optional<Reserved> pollReserved() {
        return Optional.ofNullable(reserved.pollLast());
    }

    public List<Extensions> extensions() {
        return Collections.unmodifiableList(extensions);
    }

    public Optional<Extensions> pollExtensions() {
        return Optional.ofNullable(extensions.pollLast());
    }

    public void addReservedRange(int min) {
        var range = new ReservedRange();
        range.setMin(min);
        this.reserved.add(range);
    }

    public boolean addReservedIndex(int value) {
        if(hasReservedIndex(value)) {
            return false;
        }
        
        var indexes = new ReservedIndexes();
        indexes.addValue(value);
        this.reserved.add(indexes);
        return true;
    }

    public boolean addReservedName(String value) {
        if(hasReservedName(value)) {
            return false;
        }
        
        var names = new ReservedNames();
        names.addValue(value);
        this.reserved.add(names);
        return true;
    }

    public void addExtensionsRange(int min) {
        var range = new ExtensionsRange();
        range.setMin(min);
        this.extensions.add(range);
    }

    public boolean addExtensionsIndex(int value) {
        if(hasExtensionsIndex(value)) {
            return false;
        }
        
        var indexes = new ExtensionsIndexes();
        indexes.addValue(value);
        this.extensions.add(indexes);
        return true;
    }

    public sealed interface Reserved {
        boolean isAttributed();
        void setAttributed(boolean attributed);
    }

    public boolean hasReservedIndex(int value) {
        return reserved.stream().anyMatch(entry -> switch (entry) {
            case ProtobufObjectTree.ReservedIndexes reservedIndexes -> reservedIndexes.hasValue(value);
            case ProtobufObjectTree.ReservedRange reservedRange -> reservedRange.hasValue(value);
            case ProtobufObjectTree.ReservedNames ignored -> false;
        });
    }

    public boolean hasReservedName(String value) {
        return value != null && reserved.stream()
                .anyMatch(entry -> entry instanceof ProtobufObjectTree<?>.ReservedNames reservedNames && reservedNames.hasValue(value));
    }

    public boolean hasExtensionsIndex(int value) {
        return extensions.stream().anyMatch(entry -> switch (entry) {
            case ProtobufObjectTree.ExtensionsIndexes extensionsIndexes -> extensionsIndexes.hasValue(value);
            case ProtobufObjectTree.ExtensionsRange extensionsRange -> extensionsRange.hasValue(value);
        });
    }

    public final class ReservedRange implements Reserved {
        private Integer min;
        private Integer max;

        public OptionalInt min() {
            return min == null ? OptionalInt.empty() : OptionalInt.of(min);
        }

        public boolean setMin(Integer min) {
            this.min = min;
            return true;
        }

        public OptionalInt max() {
            return max == null ? OptionalInt.empty() : OptionalInt.of(max);
        }

        public boolean setMax(Integer max) {
            if (hasReservedIndex(max)) {
                return false;
            }

            this.max = max;
            return true;
        }

        public boolean hasValue(int entry) {
            return (min != null && entry >= min) && (max != null && entry <= max);
        }

        @Override
        public boolean isAttributed() {
            return min != null && max != null;
        }

        @Override
        public void setAttributed(boolean attributed) {
            if (!attributed) {
                return;
            }

            if(min == null) {
                throw new ProtobufInternalException("Min");
            }

            if(max == null) {
                throw new ProtobufInternalException("Max");
            }
        }

        @Override
        public String toString() {
            return "%s to %s".formatted(min, max != null && max == Integer.MAX_VALUE ? "max" : max);
        }
    }

    public final class ReservedIndexes implements Reserved {
        private final LinkedList<Integer> values;
        private boolean attributed;
        private ReservedIndexes() {
            this.values = new LinkedList<>();
        }

        public boolean hasValue(int value) {
            return values.contains(value);
        }

        public boolean addValue(int value) {
            if(hasReservedIndex(value)) {
                return false;
            }

            values.add(value);
            return true;
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public Integer pollLastValue() {
            return values.pollLast();
        }

        @Override
        public boolean isAttributed() {
            return attributed;
        }

        public void setAttributed(boolean attributed) {
            this.attributed = attributed;
        }

        @Override
        public String toString() {
            return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }

        public Collection<Integer> values() {
            return Collections.unmodifiableCollection(values);
        }
    }

    public final class ReservedNames implements Reserved {
        private final Collection<String> values;
        private boolean attributed;
        private ReservedNames() {
            this.values = new ArrayList<>();
        }

        public boolean hasValue(String value) {
            return values.contains(value);
        }

        public boolean addValue(String value) {
            if(hasReservedName(value)) {
                return false;
            }

            values.add(value);
            return true;
        }

        @Override
        public boolean isAttributed() {
            return attributed;
        }

        public void setAttributed(boolean attributed) {
            this.attributed = attributed;
        }

        @Override
        public String toString() {
            return values.stream()
                    .map("\"%s\""::formatted)
                    .collect(Collectors.joining(", "));
        }

        public Collection<String> values() {
            return Collections.unmodifiableCollection(values);
        }
    }

    public sealed interface Extensions {
        boolean isAttributed();
        void setAttributed(boolean attributed);
    }

    public final class ExtensionsRange implements Extensions {
        private Integer min;
        private Integer max;

        public OptionalInt min() {
            return min == null ? OptionalInt.empty() : OptionalInt.of(min);
        }

        public boolean setMin(Integer min) {
            this.min = min;
            return true;
        }

        public OptionalInt max() {
            return max == null ? OptionalInt.empty() : OptionalInt.of(max);
        }

        public boolean setMax(Integer max) {
            if(hasExtensionsIndex(max)) {
                return false;
            }

            this.max = max;
            return true;
        }

        private boolean hasValue(Integer entry) {
            return (min != null && entry >= min) && (max != null && entry <= max);
        }

        @Override
        public boolean isAttributed() {
            return min != null && max != null;
        }

        @Override
        public void setAttributed(boolean attributed) {
            if (!attributed) {
                return;
            }

            if(min == null) {
                throw new ProtobufInternalException("Min");
            }

            if(max == null) {
                throw new ProtobufInternalException("Max");
            }
        }

        @Override
        public String toString() {
            return "%s to %s".formatted(min, max == Integer.MAX_VALUE ? "max" : max);
        }
    }

    public final class ExtensionsIndexes implements Extensions {
        private final LinkedList<Integer> values;
        private boolean attributed;
        private ExtensionsIndexes() {
            this.values = new LinkedList<>();
        }

        public boolean hasValue(int value) {
            return values.contains(value);
        }

        public boolean addValue(int value) {
            if(hasExtensionsIndex(value)) {
                return false;
            }

            values.add(value);
            return true;
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public Integer pollLastValue() {
            return values.pollLast();
        }

        @Override
        public boolean isAttributed() {
            return attributed;
        }

        public void setAttributed(boolean attributed) {
            this.attributed = attributed;
        }

        @Override
        public String toString() {
            return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }

        public Collection<Integer> values() {
            return Collections.unmodifiableCollection(values);
        }
    }

    @Override
    public String toString() {
        var instructionName = switch (this) {
            case ProtobufMessageTree ignored -> "message";
            case ProtobufEnumTree ignored -> "enum";
            default -> throw new IllegalStateException("Unexpected value: " + this);
        };
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        var builder = new StringBuilder();
        builder.append(toLeveledString("%s %s {\n".formatted(instructionName, name)));
        reserved.stream()
                .map(entry -> toLeveledString("reserved " + entry + ";\n", 1))
                .forEach(builder::append);
        extensions.stream()
                .map(entry -> toLeveledString("extensions " + entry + ";\n", 1))
                .forEach(builder::append);
        statements().forEach(statement -> {
            builder.append(statement.toString());
            builder.append("\n");
        });
        builder.append(toLeveledString("}"));
        return builder.toString();
    }
}
