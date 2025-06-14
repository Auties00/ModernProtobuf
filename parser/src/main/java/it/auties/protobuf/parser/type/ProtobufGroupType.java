package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufGroupTree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufGroupType implements ProtobufTypeReference {
    private final String name;
    private ProtobufGroupTree declaration;

    public static ProtobufGroupType unattributed(String typeName){
        return new ProtobufGroupType(Objects.requireNonNull(typeName), null);
    }

    public static ProtobufGroupType attributed(String typeName, ProtobufGroupTree typeDeclaration){
        return new ProtobufGroupType(Objects.requireNonNull(typeName), Objects.requireNonNull(typeDeclaration));
    }

    private ProtobufGroupType(String name, ProtobufGroupTree declaration){
        this.name = name;
        this.declaration = declaration;
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.GROUP;
    }

    @Override
    public String name() {
        if(declaration == null) {
            return name;
        }

        return declaration.qualifiedName();
    }

    @Override
    public String toString() {
        return "group";
    }

    public Optional<ProtobufGroupTree> declaration() {
        return Optional.ofNullable(declaration);
    }

    @Override
    public boolean isAttributed(){
        return declaration != null;
    }

    public void attribute(ProtobufGroupTree statement) {
        this.declaration = statement;
    }
}
