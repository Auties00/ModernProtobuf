package it.auties.protobuf.parser.tree;

import java.util.Optional;

public sealed interface ProtobufNamedTree permits ProtobufBodyTree, ProtobufFieldTree {
    Optional<String> name();
}
