package it.auties.protobuf.parser.tree;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Optional;

public abstract sealed class ProtobufFieldTree extends ProtobufIndexedTree implements ProtobufNamedTree, ProtobufOptionedTree permits ProtobufEnumConstantTree, ProtobufTypedFieldTree {
    String name;
    final LinkedHashMap<String, ProtobufOptionTree> options;
    private ProtobufOptionTree lastOption;

    protected ProtobufFieldTree() {
        this.options = new LinkedHashMap<>();
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    @Override
    public Collection<ProtobufOptionTree> options() {
        return Collections.unmodifiableCollection(options.values());
    }

    @Override
    public ProtobufFieldTree addOption(String name) {
        var option = new ProtobufOptionTree(name);
        options.put(name, option);
        this.lastOption = option;
        return this;
    }

    @Override
    public Optional<ProtobufOptionTree> lastOption() {
        return Optional.ofNullable(lastOption);
    }

    public ProtobufFieldTree setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public boolean isAttributed() {
        return super.isAttributed() && name != null;
    }
}
