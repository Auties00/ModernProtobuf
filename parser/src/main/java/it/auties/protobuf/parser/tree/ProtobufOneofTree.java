package it.auties.protobuf.parser.tree;

import java.util.Locale;
import java.util.Optional;

public final class ProtobufOneofTree
        extends ProtobufNameableBlock<ProtobufOneofTree, ProtobufOneofChildTree>
        implements ProtobufMessageChildTree, ProtobufGroupChildTree {
    public ProtobufOneofTree(int line, String name) {
        super(line, name, false);
    }

    public Optional<String> className() {
        if(name == null) {
            return Optional.empty();
        }

        return Optional.of(name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Seal");
    }

    @Override
    public String toString() {
        return "oneof " + name + super.toString();
    }
}
