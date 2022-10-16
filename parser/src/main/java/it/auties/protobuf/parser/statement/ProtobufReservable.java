package it.auties.protobuf.parser.statement;

import java.util.*;
import java.util.stream.Collectors;

public sealed abstract class ProtobufReservable<T extends ProtobufStatement> extends ProtobufObject<T>
        permits ProtobufMessageStatement, ProtobufEnumStatement {
    private final TreeSet<String> reservedNames;
    private final TreeSet<Integer> reservedIndexes;
    public ProtobufReservable(String name, String packageName, ProtobufObject<?> parent) {
        super(name, packageName, parent);
        this.reservedNames = new TreeSet<>();
        this.reservedIndexes = new TreeSet<>();
    }

    public Set<Integer> reservedIndexes() {
        return reservedIndexes;
    }

    public Set<String> reservedNames() {
        return reservedNames;
    }

    protected String toPrettyReservable(int level) {
        var builder = new StringBuilder();
        if (!reservedNames().isEmpty()) {
            builder.append(INDENTATION.repeat(level));
            builder.append("reserved %s;".formatted(toPrettyReservedNames()));
            builder.append("\n");
            return builder.toString();
        }

        if (reservedIndexes().isEmpty()) {
            return builder.toString();
        }

        builder.append(INDENTATION.repeat(level));
        builder.append("reserved %s;".formatted(toPrettyReservedIndexes()));
        builder.append("\n");
        return builder.toString();
    }

    private String toPrettyReservedNames() {
        return reservedNames().stream()
                .map("\"%s\""::formatted)
                .collect(Collectors.joining(", "));
    }

    private String toPrettyReservedIndexes() {
        var iterator = reservedIndexes().iterator();
        Integer start = null;
        Integer end = null;
        var results = new ArrayList<String>();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (start == null) {
                start = end = next;
                continue;
            }

            if (next == end + 1) {
                end = next;
                continue;
            }

            results.add(String.valueOf(next));
            results.add("%s to %s".formatted(start, end));
            start = end = null;
        }

        if (start != null) {
            if (start.equals(end)) {
                results.add(String.valueOf(start));
            } else {
                results.add("%s to %s".formatted(start, end));
            }
        }

        return String.join(", ", results);
    }
}
