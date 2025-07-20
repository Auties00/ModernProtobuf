package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufEnumStatement;
import it.auties.protobuf.parser.tree.ProtobufTree;

import java.util.Objects;

public final class ProtobufMessageOrEnumTypeReference implements ProtobufTypeReference {
    private final String name;
    private ProtobufTree.WithBodyAndName<?> declaration;

    public ProtobufMessageOrEnumTypeReference(String name){
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    public ProtobufMessageOrEnumTypeReference(ProtobufTree.WithBodyAndName<?> declaration) {
        Objects.requireNonNull(declaration, "declaration cannot be null");
        this.name = declaration.name();
        this.declaration = declaration;
    }

    @Override
    public ProtobufType protobufType() {
        if (declaration == null) {
            return ProtobufType.UNKNOWN;
        }

        return declaration instanceof ProtobufEnumStatement ? ProtobufType.ENUM : ProtobufType.MESSAGE;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name();
    }

    public ProtobufTree.WithBodyAndName<?> declaration() {
        return declaration;
    }

    public boolean hasDeclaration() {
        return declaration != null;
    }

    public void setDeclaration(ProtobufTree.WithBodyAndName<?> statement) {
        this.declaration = statement;
    }

    @Override
    public boolean isAttributed(){
        return declaration != null;
    }
}
