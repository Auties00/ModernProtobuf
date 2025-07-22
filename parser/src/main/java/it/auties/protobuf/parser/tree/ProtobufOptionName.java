package it.auties.protobuf.parser.tree;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public record ProtobufOptionName(String name, boolean extension, List<String> membersSelected) {
    @Override
    public List<String> membersSelected() {
        return Collections.unmodifiableList(membersSelected);
    }

    @Override
    public String toString() {
        var result = new StringBuilder();
        if (extension) {
            result.append('(');
        }
        result.append(name);
        if (extension) {
            result.append(')');
        }
        for (var member : membersSelected) {
            result.append('.');
            result.append(member);
        }
        return result.toString();
    }
}
