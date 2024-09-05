package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.tree.body.ProtobufBodyTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufFieldTree;

import java.util.Optional;

public sealed interface ProtobufNamedTree extends ProtobufTree permits ProtobufBodyTree, ProtobufFieldTree {
    Optional<String> name();
}
