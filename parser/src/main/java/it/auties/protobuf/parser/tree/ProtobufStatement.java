package it.auties.protobuf.parser.tree;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.stream.Collectors;

public sealed abstract class ProtobufStatement
        implements ProtobufTree
        permits ProtobufBlock, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufImportStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufReservedStatement, ProtobufSyntaxStatement {
    private final int line;
    protected ProtobufBlock<?, ?> parent;
    protected int level;
    private final LinkedHashMap<String, ProtobufOptionStatement> options;
    protected ProtobufStatement(int line) {
        this.line = line;
        this.options = new LinkedHashMap<>();
        if(this instanceof ProtobufFieldStatement typeableField) {
            var defaultValueOption = new ProtobufOptionStatement(line, "default", typeableField);
            options.put(defaultValueOption.name(), defaultValueOption);
        }
    }

    @Override
    public int line() {
        return line;
    }

    public Optional<ProtobufBlock<?, ?>> parent() {
        return Optional.ofNullable(parent);
    }

    public void setParent(ProtobufBlock<?, ?> parent, int nestedLevelOffset) {
        this.parent = parent;
        var parentLevel = parent.level;
        this.level = parentLevel + nestedLevelOffset;
    }

    public SequencedCollection<ProtobufOptionStatement> options() {
        return options.sequencedValues();
    }

    public void addOption(String key) {
        var option = new ProtobufOptionStatement(line, key, null);
        options.put(key, option);
    }

    public Optional<Object> getOption(String key) {
        return Optional.ofNullable(options.get(key));
    }

    protected String optionsToString() {
        return options.isEmpty() ? ";" : " [" + options.values()
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) + "];";
    }

    protected String toLeveledString(String input) {
        return "    ".repeat(this.level == 0 ? 0 : this.level - 1) + input;
    }
}
