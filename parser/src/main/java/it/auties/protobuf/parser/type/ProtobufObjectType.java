package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufObject;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class ProtobufObjectType implements ProtobufTypeReference {
    private final String name;
    private ProtobufObject<?> declaration;

    public static ProtobufObjectType unattributed(String typeName){
        return new ProtobufObjectType(typeName, null);
    }

    public static ProtobufObjectType attributed(String typeName, ProtobufMessageStatement typeDeclaration){
        return new ProtobufObjectType(typeName, typeDeclaration);
    }

    private ProtobufObjectType(String name, ProtobufMessageStatement declaration){
        this.name = name;
        this.declaration = declaration;
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.OBJECT;
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
