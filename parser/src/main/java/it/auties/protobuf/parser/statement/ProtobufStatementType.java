package it.auties.protobuf.parser.statement;

public enum ProtobufStatementType {
    DOCUMENT,
    ENUM,
    FIELD,
    MESSAGE,
    ONE_OF;

    public boolean canBeNested(){
        return this == MESSAGE || this == ENUM;
    }
}
