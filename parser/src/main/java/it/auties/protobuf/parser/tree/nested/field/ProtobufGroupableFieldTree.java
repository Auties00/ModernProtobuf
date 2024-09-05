package it.auties.protobuf.parser.tree.nested.field;

import it.auties.protobuf.parser.tree.body.object.ProtobufGroupTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufGroupableChildTree;
import it.auties.protobuf.parser.tree.body.oneof.ProtobufOneofChildTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionTree;
import it.auties.protobuf.parser.type.ProtobufGroupType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ProtobufGroupableFieldTree extends ProtobufFieldTree implements ProtobufGroupableChildTree, ProtobufOneofChildTree {
    private Modifier modifier;
    private ProtobufTypeReference type;
    public ProtobufGroupableFieldTree(int line) {
        super(line);
    }

    public Optional<Modifier> modifier() {
        return Optional.ofNullable(modifier);
    }

    public Optional<ProtobufTypeReference> type() {
        return Optional.ofNullable(type);
    }

    public ProtobufGroupableFieldTree setType(ProtobufTypeReference type) {
        this.type = type;
        return this;
    }

    public ProtobufGroupableFieldTree setModifier(Modifier modifier) {
        this.modifier = modifier;
        return this;
    }

    @Override
    public boolean isAttributed() {
        return super.isAttributed() && type != null && modifier != null;
    }

    @Override
    public String toString() {
        var modifier = Optional.ofNullable(this.modifier)
                .filter(entry -> entry.type() != Modifier.Type.NOTHING)
                .map(entry -> entry + " ")
                .orElse("");
        var type = Objects.requireNonNull(this.type, "[missing]");
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        var index = Objects.requireNonNull(this.index, "[missing]");
        var optionsString = options.isEmpty() ? "" : " [" + options.values()
                .stream()
                .map(ProtobufOptionTree::toString)
                .collect(Collectors.joining(", ")) + "]";
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
}
