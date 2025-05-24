package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufGroupType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public sealed class ProtobufFieldStatement
        extends ProtobufStatement
        implements ProtobufNameableTree, ProtobufIndexableTree, ProtobufOneofChildTree, ProtobufMessageChildTree, ProtobufGroupChildTree
        permits ProtobufEnumConstantStatement {
    protected ProtobufFieldModifier modifier;
    protected ProtobufTypeReference type;
    protected String name;
    protected Integer index;

    public ProtobufFieldStatement(int line) {
        super(line);
    }

    @Override
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public ProtobufFieldStatement setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public OptionalInt index() {
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    public ProtobufFieldStatement setIndex(Integer index) {
        this.index = index;
        return this;
    }

    public Optional<ProtobufTypeReference> type() {
        return Optional.ofNullable(type);
    }

    public ProtobufFieldStatement setType(ProtobufTypeReference type) {
        this.type = type;
        return this;
    }

    public ProtobufFieldStatement setModifier(ProtobufFieldModifier modifier) {
        this.modifier = modifier;
        return this;
    }

    @Override
    public boolean isAttributed() {
        return index != null && name != null && modifier != null && type != null;
    }

    @Override
    public String toString() {
        var modifier = Optional.ofNullable(this.modifier)
                .filter(entry -> entry.type() != ProtobufFieldModifier.Type.NOTHING)
                .map(entry -> entry + " ")
                .orElse("");
        var type = Objects.requireNonNull(this.type, "[missing]");
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        var index = Objects.requireNonNull(this.index, "[missing]");
        var optionsString = optionsToString();
        var end = type instanceof ProtobufGroupType ? "" : ";";
        var string = toLeveledString(modifier + type + " " + name + " = " + index + optionsString + end);
        if(type instanceof ProtobufGroupType groupType) {
            var body = groupType.declaration()
                    .map(ProtobufGroupTree::toString)
                    .orElse("[missing];");
            return string + " " + body;
        }else {
            return string;
        }
    }

    public Optional<ProtobufFieldModifier> modifier() {
        return Optional.ofNullable(modifier);
    }
}
