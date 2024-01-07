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
        return "    ".repeat(nestedLevel == 0 ? 0 : nestedLevel - 1) + input;
    }
}
