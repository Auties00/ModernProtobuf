package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufGroupField;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufGroupType implements ProtobufTypeReference {
    private final String name;
    private ProtobufGroupField declaration;

    public static ProtobufGroupType of(String typeName){
        Objects.requireNonNull(typeName, "typeName cannot be null");
        return new ProtobufGroupType(typeName, null);
    }

    public static ProtobufGroupType of(String typeName, ProtobufGroupField typeDeclaration){
        return new ProtobufGroupType(typeName, Objects.requireNonNull(typeDeclaration));
    }

    private ProtobufGroupType(String name, ProtobufGroupField declaration){
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

    public Optional<ProtobufGroupField> declaration() {
        return Optional.ofNullable(declaration);
    }

    @Override
    public boolean isAttributed(){
        return declaration != null;
    }

    public void attribute(ProtobufGroupField statement) {
        this.declaration = statement;
    }
}
