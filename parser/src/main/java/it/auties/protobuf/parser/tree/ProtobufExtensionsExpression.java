package it.auties.protobuf.parser.tree;

public sealed interface ProtobufExtensionsExpression
        extends ProtobufExpression
        permits ProtobufIntegerExpression, ProtobufRangeExpression {
}
