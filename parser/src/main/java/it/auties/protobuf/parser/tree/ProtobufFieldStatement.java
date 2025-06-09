package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufGroupType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Objects;
import java.util.Optional;

public sealed class ProtobufFieldStatement
        extends ProtobufStatement
        implements ProtobufNamedTree, ProtobufIndexedTree, ProtobufOneofChildTree, ProtobufMessageChildTree, ProtobufGroupChildTree
        permits ProtobufEnumConstantStatement {
    protected ProtobufFieldModifier modifier;
    protected ProtobufTypeReference type;
    protected String name;
    protected Integer index;

    public ProtobufFieldStatement(int line) {
        super(line);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasName() {
        return name != null;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public boolean hasIndex() {
        return index != null;
    }

    @Override
    public void setIndex(int index) {
        this.index = index;
    }

    public ProtobufTypeReference type() {
        return type;
    }

    public boolean hasType() {
        return type != null;
    }

    public void setType(ProtobufTypeReference type) {
        this.type = type;
    }

    public ProtobufFieldModifier modifier() {
        return modifier;
    }

    public boolean hasModifier() {
        return modifier != null;
    }

    public void setModifier(ProtobufFieldModifier modifier) {
        this.modifier = modifier;
    }

    @Override
    public boolean isAttributed() {
        return hasIndex() && hasName() && hasModifier() && hasType();
    }

    @Override
    public String toString() {
        var modifier = Optional.ofNullable(this.modifier)
                .filter(entry -> entry.type() != ProtobufFieldModifier.Type.NOTHING)
                .map(entry -> entry + " ")
                .orElse("");
        var type = Objects.requireNonNullElse(this.type.toString(), "<missing>");
        var name = Objects.requireNonNullElse(this.name, "<unknown>");
        var index = Objects.requireNonNullElse(this.index, "<unknown>");
        var optionsString = optionsToString();
        var end = this.type instanceof ProtobufGroupType ? "" : ";";
        var string = toLeveledString(modifier + type + " " + name + " = " + index + optionsString + end);
        if(this.type instanceof ProtobufGroupType groupType) {
            var body = groupType.declaration()
                    .map(ProtobufGroupTree::toString)
                    .orElse("[missing];");
            return string + " " + body;
        }else {
            return string;
        }
    }
}
