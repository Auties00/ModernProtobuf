package it.auties.protobuf.parser.tree;

public sealed interface ProtobufExtensionsChild
        extends ProtobufExpression
        permits ProtobufIntegerExpression, ProtobufRangeExpression {
}
