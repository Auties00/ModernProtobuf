package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufEnum;
import it.auties.protobuf.parser.tree.ProtobufTree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufMessageOrEnumType implements ProtobufTypeReference {
    private final String name;
    private ProtobufTree declaration;

    public static ProtobufMessageOrEnumType of(String typeName) {
        Objects.requireNonNull(typeName, "typeName cannot be null");
        return new ProtobufMessageOrEnumType(typeName, null);
    }

    public static ProtobufMessageOrEnumType of(String typeName, ProtobufTree typeDeclaration){
        Objects.requireNonNull(typeName, "typeName cannot be null");
        return new ProtobufMessageOrEnumType(typeName, typeDeclaration);
    }

    private ProtobufMessageOrEnumType(String name, ProtobufTree declaration){
        this.name = name;
        this.declaration = declaration;
    }

    @Override
    public ProtobufType protobufType() {
        if (declaration == null) {
            return ProtobufType.UNKNOWN;
        }

        return declaration instanceof ProtobufEnum ? ProtobufType.ENUM : ProtobufType.MESSAGE;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name();
    }

    public Optional<ProtobufTree> declaration() {
        return Optional.ofNullable(declaration);
    }

    @Override
    public boolean isAttributed(){
        return declaration != null;
    }

    public void setDeclaration(ProtobufTree statement) {
        this.declaration = statement;
    }
}
