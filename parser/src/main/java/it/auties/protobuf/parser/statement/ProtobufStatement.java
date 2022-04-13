package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.object.ProtobufObject;

public sealed interface ProtobufStatement permits ProtobufObject, EnumConstantStatement, FieldStatement {
}
