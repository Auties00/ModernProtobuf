package it.auties.protobuf.parser.object;

import it.auties.protobuf.parser.statement.EnumStatement;
import it.auties.protobuf.parser.statement.MessageStatement;

import java.util.*;
import java.util.stream.Collectors;

public sealed interface ProtobufReservable permits MessageStatement, EnumStatement {

    TreeSet<String> reservedNames();
    TreeSet<Integer> reservedIndexes();

    default void addReservedFields(int level, StringBuilder builder) {
        var indentation = "    ";
        if(!reservedNames().isEmpty()){
            builder.append(indentation.repeat(level));
            builder.append("reserved %s;".formatted(toPrettyReservedNames()));
            builder.append("\n");
            return;
        }

        if (reservedIndexes().isEmpty()) {
            return;
        }

        builder.append(indentation.repeat(level));
        builder.append("reserved %s;".formatted(toPrettyReservedIndexes()));
        builder.append("\n");
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
        while (iterator.hasNext()){
            var next = iterator.next();
            if(start == null){
                start = end = next;
                continue;
            }

            if(next == end + 1){
                end = next;
                continue;
            }

            results.add(String.valueOf(next));
            results.add("%s to %s".formatted(start, end));
            start = end = null;
        }

        if(start != null){
            if(start.equals(end)){
                results.add(String.valueOf(start));
            }else {
                results.add("%s to %s".formatted(start, end));
            }
        }

        return String.join(", ", results);
    }
}
