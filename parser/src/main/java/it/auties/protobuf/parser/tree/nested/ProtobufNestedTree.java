package it.auties.protobuf.parser.tree.nested;

import it.auties.protobuf.parser.tree.ProtobufTree;
import it.auties.protobuf.parser.tree.body.ProtobufBodyTree;
import it.auties.protobuf.parser.tree.nested.impors.ProtobufImportTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufFieldTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionTree;

import java.util.Optional;

public sealed abstract class ProtobufNestedTree implements ProtobufTree permits ProtobufBodyTree, ProtobufImportTree, ProtobufFieldTree, ProtobufOptionTree {
    private final int line;
    protected ProtobufBodyTree<?, ?> parent;
    protected int nestedLevel;
    protected ProtobufNestedTree(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }

    public Optional<ProtobufBodyTree<?, ?>> parent() {
        return Optional.ofNullable(parent);
    }

    public void setParent(ProtobufBodyTree parent, int nestedLevelOffset) {
        this.parent = parent;
        var parentLevel = parent.nestedLevel;
        this.nestedLevel = parentLevel + nestedLevelOffset;
    }

    public abstract boolean isAttributed();

    protected String toLeveledString(String input) {
        return toLeveledString(input, 0);
    }

    protected String toLeveledString(String input, int offset) {
        var level = nestedLevel + offset;
        return "    ".repeat(level == 0 ? 0 : level - 1) + input;
    }
}
