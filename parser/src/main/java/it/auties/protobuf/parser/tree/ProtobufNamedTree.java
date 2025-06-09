package it.auties.protobuf.parser.tree;

public sealed interface ProtobufNamedTree
        extends ProtobufTree
        permits ProtobufNamedBlock, ProtobufFieldStatement {
    String name();
    boolean hasName();
    void setName(String name);
}
