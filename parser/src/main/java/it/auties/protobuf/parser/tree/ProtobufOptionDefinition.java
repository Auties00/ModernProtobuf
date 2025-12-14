package it.auties.protobuf.parser.tree;

public sealed interface ProtobufOptionDefinition
        extends ProtobufTree, ProtobufTree.WithName, ProtobufTree.WithType
        permits ProtobufFieldStatement, ProtobufGroupStatement {
}
