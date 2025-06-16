package it.auties.protobuf.parser.tree;

public sealed interface ProtobufIndexedTree
        extends ProtobufTree
        permits ProtobufField {
    int index();
    boolean hasIndex();
    void setIndex(int index);
}
