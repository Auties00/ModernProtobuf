package it.auties.protobuf.parser.tree;

import java.util.Collection;
import java.util.Optional;

public sealed interface ProtobufOptionedTree<T extends ProtobufOptionedTree<T>> permits ProtobufDocument, ProtobufFieldTree{
    Collection<ProtobufOptionTree> options();

    T addOption(String value);

    Optional<ProtobufOptionTree> lastOption();
}
