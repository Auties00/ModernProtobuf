package it.auties.protobuf.parser.tree;

import java.util.Optional;

public sealed abstract class ProtobufNestedTree implements ProtobufTree permits ProtobufBodyTree, ProtobufImportTree, ProtobufIndexedTree, ProtobufOptionTree {
    ProtobufBodyTree<?> parent;
    int nestedLevel;

    public Optional<ProtobufBodyTree<?>> parent() {
        return Optional.ofNullable(parent);
    }

    public void setParent(ProtobufBodyTree parent) {
        this.parent = parent;
        var parentLevel = parent.nestedLevel;
        this.nestedLevel = parentLevel + 1;
    }

    public abstract boolean isAttributed();

    String toLeveledString(String input) {
        return toLeveledString(input, 0);
    }

    String toLeveledString(String input, int offset) {
        var level = nestedLevel + offset;
        return "    ".repeat(level == 0 ? 0 : level - 1) + input;
    }

}
