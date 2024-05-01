package it.auties.protobuf.parser.tree;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ProtobufModifiableFieldTree extends ProtobufTypedFieldTree implements ProtobufMessageChildTree{
    private ProtobufFieldModifier modifier;

    public Optional<ProtobufFieldModifier> modifier() {
        return Optional.ofNullable(modifier);
    }

    public ProtobufModifiableFieldTree setModifier(ProtobufFieldModifier modifier) {
        this.modifier = modifier;
        return this;
    }

    @Override
    public boolean isAttributed() {
        return super.isAttributed() && modifier != null;
    }

    @Override
    public String toString() {
        var modifier = Optional.ofNullable(this.modifier)
                .filter(entry -> entry != ProtobufFieldModifier.NOTHING)
                .map(entry -> entry + " ")
                .orElse("");
        var type = Objects.requireNonNull(this.type, "[missing]");
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        var index = Objects.requireNonNull(this.index, "[missing]");
        var optionsString = options.isEmpty() ? ";" : " [" + options.values()
                .stream()
                .map(ProtobufOptionTree::toString)
                .collect(Collectors.joining(", ")) + "];";
        return toLeveledString(modifier + type + " " + name + " = " + index + optionsString);
    }
}
