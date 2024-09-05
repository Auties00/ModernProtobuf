package it.auties.protobuf.parser.tree.body.oneof;

import it.auties.protobuf.parser.tree.body.ProtobufBodyTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufGroupableChildTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufGroupableFieldTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionTree;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ProtobufOneofTree extends ProtobufBodyTree<ProtobufOneofTree, ProtobufGroupableFieldTree> implements ProtobufGroupableChildTree {
    public ProtobufOneofTree(int line, String name) {
        super(line, name);
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
        return statements().stream().allMatch(ProtobufGroupableFieldTree::isAttributed);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append(toLeveledString("oneof %s {\n".formatted(Objects.requireNonNullElse(name, "[missing]"))));
        options.values()
                .stream()
                .map(ProtobufOptionTree::toString)
                .forEach(option -> {
                    builder.append(option);
                    builder.append("\n");
                });
        statements().forEach(statement -> {
            builder.append(statement.toString());
            builder.append("\n");
        });
        builder.append(toLeveledString("}"));
        return builder.toString();
    }
}
