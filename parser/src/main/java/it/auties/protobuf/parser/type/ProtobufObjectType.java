package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.parser.statement.ProtobufStatementType;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class ProtobufObjectType implements ProtobufTypeReference {
    private final String name;
    private final boolean isEnum;
    private ProtobufObject<?> declaration;

    public static ProtobufObjectType unattributed(String typeName, boolean isEnum){
        return new ProtobufObjectType(typeName, isEnum, null);
    }

    public static ProtobufObjectType attributed(String typeName, ProtobufMessageStatement typeDeclaration){
        return new ProtobufObjectType(typeName, typeDeclaration.statementType() == ProtobufStatementType.ENUM, typeDeclaration);
    }

    private ProtobufObjectType(String name, boolean isEnum, ProtobufMessageStatement declaration){
        this.name = name;
        this.declaration = declaration;
        this.isEnum = isEnum;
    }

    @Override
    public ProtobufType protobufType() {
        return isEnum ? ProtobufType.ENUM : ProtobufType.MESSAGE;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    public ProtobufObject<?> declaration() {
        return declaration;
    }

    public boolean attributed(){
        return declaration != null;
    }

    public void attribute(ProtobufObject<?> statement) {
        this.declaration = statement;
    }
}
