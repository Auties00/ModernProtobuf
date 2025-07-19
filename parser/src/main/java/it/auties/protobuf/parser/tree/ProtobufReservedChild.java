package it.auties.protobuf.parser.tree;

public sealed interface ProtobufReservedChild
        extends ProtobufExpression
        permits ProtobufIntegerExpression, ProtobufLiteralExpression, ProtobufRangeExpression {
}
