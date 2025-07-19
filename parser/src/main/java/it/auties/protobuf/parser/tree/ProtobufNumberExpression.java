package it.auties.protobuf.parser.tree;

public sealed interface ProtobufNumberExpression
        extends ProtobufExpression
        permits ProtobufFloatingPointExpression, ProtobufIntegerExpression {
    Number value();
}
