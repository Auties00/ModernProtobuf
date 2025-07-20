package it.auties.protobuf.parser.tree;

public sealed interface ProtobufReservedExpression
        extends ProtobufExpression
        permits ProtobufIntegerExpression, ProtobufLiteralExpression, ProtobufRangeExpression {
}
