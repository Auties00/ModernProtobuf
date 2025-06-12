package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.ProtobufBlock;
import it.auties.protobuf.parser.tree.ProtobufEnumTree;
import it.auties.protobuf.parser.tree.ProtobufMessageTree;
import it.auties.protobuf.parser.tree.ProtobufNamedBlock;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufObjectType implements ProtobufTypeReference {
    private final String name;
    private ProtobufNamedBlock<?> declaration;

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
        if (declaration == null) {
            return ProtobufType.UNKNOWN;
        }

        return declaration instanceof ProtobufEnumTree ? ProtobufType.ENUM : ProtobufType.MESSAGE;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name();
    }

    public Optional<ProtobufNamedBlock<?>> declaration() {
        return Optional.ofNullable(declaration);
    }

    @Override
    public boolean isAttributed(){
        return declaration != null;
    }

    public void attribute(ProtobufNamedBlock<?> statement) {
        this.declaration = statement;
    }
}
