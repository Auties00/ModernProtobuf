package it.auties.protobuf.parser.tree;

public sealed interface ProtobufExpression
        extends ProtobufTree
        permits ProtobufBoolExpression, ProtobufEnumConstantExpression, ProtobufExpressionImpl, ProtobufExtensionsExpression, ProtobufNumberExpression, ProtobufLiteralExpression, ProtobufMessageValueExpression, ProtobufNullExpression, ProtobufRangeExpression, ProtobufReservedExpression {

}
