package it.auties.protobuf.parser.tree;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public abstract sealed class ProtobufIndexedBodyTree<T extends ProtobufTree> extends ProtobufBodyTree<T> permits ProtobufObjectTree, ProtobufOneOfTree {
    String name;
    ProtobufIndexedBodyTree(String name) {
        this.name = name;
    }

    @Override
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public ProtobufIndexedBodyTree setName(String name) {
        this.name = name;
        return this;
    }

    public List<Integer> indexes() {
        return statements().stream()
                .filter(entry -> entry instanceof ProtobufIndexedTree)
                .map(entry -> (ProtobufIndexedTree) entry)
                .map(ProtobufIndexedTree::index)
                .flatMapToInt(OptionalInt::stream)
                .boxed()
                .toList();
    }

    public boolean hasIndex(int index) {
        return statements()
                .stream()
                .filter(entry -> entry instanceof ProtobufIndexedTree)
                .map(entry -> (ProtobufIndexedTree) entry)
                .anyMatch(entry -> entry.index().isPresent() && entry.index().getAsInt() == index);
    }

    @Override
    public boolean isAttributed() {
        return name != null;
    }
}
