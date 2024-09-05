package it.auties.protobuf.parser.tree.nested.field;

import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionTree;

import java.util.Objects;
import java.util.stream.Collectors;

public final class ProtobufEnumConstantTree extends ProtobufFieldTree {
    public ProtobufEnumConstantTree(int line) {
        super(line);
    }

    @Override
    public String toString() {
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        var index = Objects.requireNonNull(this.index, "[missing]");
        var optionsString = options.isEmpty() ? ";" : " [" + options.values()
                .stream()
                .map(ProtobufOptionTree::toString)
                .collect(Collectors.joining(", ")) + "];";
        return toLeveledString(name + " = " + index + optionsString);
    }
}
