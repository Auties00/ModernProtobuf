package it.auties.protobuf.parser.tree;

public sealed interface ProtobufExpression
        extends ProtobufTree
        permits ProtobufBoolExpression, ProtobufEnumConstantExpression, ProtobufExpressionImpl, ProtobufExtensionsChild, ProtobufIntegerExpression, ProtobufLiteralExpression, ProtobufMessageValueExpression, ProtobufNullExpression, ProtobufRangeExpression, ProtobufReservedChild {

}
