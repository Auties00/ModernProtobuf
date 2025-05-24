package it.auties.protobuf.parser.tree;

import java.util.Optional;

public sealed interface ProtobufNameableTree
        extends ProtobufTree
        permits ProtobufNameableBlock, ProtobufFieldStatement {
    Optional<String> name();
}
