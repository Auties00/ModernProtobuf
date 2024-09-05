package it.auties.protobuf.parser.tree.nested.option;

import it.auties.protobuf.parser.tree.body.ProtobufBodyTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufFieldTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufGroupableFieldTree;

import java.util.Collection;
import java.util.Optional;

public sealed interface ProtobufOptionedTree<T extends ProtobufOptionedTree<T>> permits ProtobufBodyTree, ProtobufFieldTree {
    Collection<ProtobufOptionTree> options();

    T addOption(int line, String value, ProtobufGroupableFieldTree definition);

    Optional<ProtobufOptionTree> lastOption();

    Optional<ProtobufOptionTree> getOption(String name);
}
