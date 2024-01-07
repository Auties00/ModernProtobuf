package it.auties.protobuf.parser.tree;

import java.util.OptionalInt;

public abstract sealed class ProtobufIndexedTree extends ProtobufNestedTree permits ProtobufFieldTree {
    Integer index;

    public OptionalInt index() {
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    public ProtobufIndexedTree setIndex(Integer index) {
        this.index = index;
        return this;
    }


    @Override
    public boolean isAttributed() {
        return index != null;
    }
}
