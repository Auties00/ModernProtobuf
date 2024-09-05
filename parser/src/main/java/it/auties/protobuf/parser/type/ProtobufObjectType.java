package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.body.object.ProtobufMessageTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufObjectTree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufObjectType implements ProtobufTypeReference {
    private final String name;
    private ProtobufObjectTree<?, ?> declaration;

    public static ProtobufObjectType unattributed(String typeName){
        return new ProtobufObjectType(Objects.requireNonNull(typeName), null);
    }

    public static ProtobufObjectType attributed(String typeName, ProtobufMessageTree typeDeclaration){
        return new ProtobufObjectType(Objects.requireNonNull(typeName), Objects.requireNonNull(typeDeclaration));
    }

    private ProtobufObjectType(String name, ProtobufMessageTree declaration){
        this.name = name;
        this.declaration = declaration;
    }

    @Override
    public ProtobufType protobufType() {
        return ProtobufType.OBJECT;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name();
    }

    public Optional<ProtobufObjectTree<?, ?>> declaration() {
        return Optional.ofNullable(declaration);
    }

    @Override
    public boolean isAttributed(){
        return declaration != null;
    }

    public void attribute(ProtobufObjectTree<?, ?> statement) {
        this.declaration = statement;
    }
}
