package it.auties.protobuf.parser.tree;

public sealed interface ProtobufNamedTree
        extends ProtobufTree
        permits ProtobufEnum, ProtobufField, ProtobufMessage, ProtobufMethod, ProtobufOneof, ProtobufService {
    String name();
    boolean hasName();
    void setName(String name);
}
