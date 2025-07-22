package it.auties.protobuf.parser.tree;

public sealed interface ProtobufExpression
        extends ProtobufTree
        permits ProtobufBoolExpression, ProtobufEnumConstantExpression, ProtobufExpressionImpl, ProtobufExtensionsExpression, ProtobufLiteralExpression, ProtobufMessageValueExpression, ProtobufNullExpression, ProtobufNumberExpression, ProtobufOptionExpression, ProtobufRangeExpression, ProtobufReservedExpression {

}
