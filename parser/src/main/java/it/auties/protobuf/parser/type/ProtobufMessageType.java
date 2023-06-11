package it.auties.protobuf.parser.type;

import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufObject;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class ProtobufMessageType implements ProtobufTypeReference {
    private final String name;
    private ProtobufObject<?> declaration;

    public static ProtobufMessageType unattributed(String typeName){
        return new ProtobufMessageType(typeName, null);
    }

    public static ProtobufMessageType attributed(String typeName, ProtobufMessageStatement typeDeclaration){
        return new ProtobufMessageType(typeName, typeDeclaration);
    }

    private ProtobufMessageType(String name, ProtobufMessageStatement declaration){
        this.name = name;
        this.declaration = declaration;
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.MESSAGE;
    }

    @Override
    public boolean primitive() {
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
