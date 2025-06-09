package it.auties.protobuf.parser.tree;

public sealed interface ProtobufIndexedTree
        extends ProtobufTree
        permits ProtobufFieldStatement {
    int index();
    boolean hasIndex();
    void setIndex(int index);
}
