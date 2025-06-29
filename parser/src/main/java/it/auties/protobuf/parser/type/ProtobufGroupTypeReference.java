package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufGroupFieldStatement;

import java.util.Objects;

public final class ProtobufGroupTypeReference implements ProtobufTypeReference {
    private final String name;
    private ProtobufGroupFieldStatement declaration;

    public ProtobufGroupTypeReference(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    public ProtobufGroupTypeReference(String name, ProtobufGroupFieldStatement declaration){
        this.name = Objects.requireNonNull(name, "name cannot be null");
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

        return declaration.name();
    }

    public ProtobufGroupFieldStatement declaration() {
        return declaration;
    }

    public boolean hasDeclaration() {
        return declaration != null;
    }

    public void setDeclaration(ProtobufGroupFieldStatement statement) {
        this.declaration = statement;
    }

    @Override
    public String toString() {
        return "group";
    }

    @Override
    public boolean isAttributed(){
        return declaration != null;
    }
}
