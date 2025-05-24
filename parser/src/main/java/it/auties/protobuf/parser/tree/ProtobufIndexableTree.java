package it.auties.protobuf.parser.tree;

import java.util.OptionalInt;

public sealed interface ProtobufIndexableTree
        extends ProtobufTree
        permits ProtobufFieldStatement {
    OptionalInt index();
}
