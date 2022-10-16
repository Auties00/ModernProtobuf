package it.auties.protobuf.parser.type;

import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;

public final class ProtobufMessageType implements ProtobufTypeReference {
    private final String typeName;
    private ProtobufObject<?> typeDeclaration;

    public static ProtobufMessageType unattributed(String typeName){
        return new ProtobufMessageType(typeName, null);
    }

    public static ProtobufMessageType attributed(String typeName, ProtobufMessageStatement typeDeclaration){
        return new ProtobufMessageType(typeName, typeDeclaration);
    }

    private ProtobufMessageType(String typeName, ProtobufMessageStatement typeDeclaration){
        this.typeName = typeName;
        this.typeDeclaration = typeDeclaration;
    }

    @Override
    public ProtobufType type() {
        return ProtobufType.MESSAGE;
    }

    @Override
    public boolean primitive() {
        return false;
    }

    public String name(){
        return typeName;
    }

    public ProtobufObject<?> declaration() {
        return typeDeclaration;
    }

    public boolean attributed(){
        return typeDeclaration != null;
    }

    public void attribute(ProtobufObject<?> statement) {
        this.typeDeclaration = statement;
    }
}
