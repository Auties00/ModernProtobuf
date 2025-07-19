package it.auties.protobuf.parser.tree;

public sealed interface ProtobufExpression
        extends ProtobufTree
        permits ProtobufBoolExpression, ProtobufEnumConstantExpression, ProtobufExpressionImpl, ProtobufExtensionsChild, ProtobufNumberExpression, ProtobufLiteralExpression, ProtobufMessageValueExpression, ProtobufNullExpression, ProtobufRangeExpression, ProtobufReservedChild {

}
