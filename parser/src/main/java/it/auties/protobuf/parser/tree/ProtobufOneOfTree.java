package it.auties.protobuf.parser.tree;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ProtobufOneOfTree extends ProtobufIndexedBodyTree<ProtobufModifiableFieldTree> implements ProtobufMessageChildTree {
    public ProtobufOneOfTree(String name) {
        super(name);
    }

    @Override
    public Optional<String> name() {
        return super.name()
                .map(name -> name.replaceFirst("(?i)oneof", ""));
    }

    public Optional<String> className() {
        return name().map(name -> name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Seal");
    }

    @Override
    public boolean isAttributed() {
        return statements().stream().allMatch(ProtobufModifiableFieldTree::isAttributed);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder()
                .append("oneof")
                .append(" ")
                .append(Objects.requireNonNullElse(name, "[missing]"))
                .append(" ")
                .append("{")
                .append("\n");
        statements().forEach(statement -> {
            builder.append(statement.toString());
            builder.append("\n");
        });

        builder.append("}");
        return toLeveledString(builder.toString());
    }
}
