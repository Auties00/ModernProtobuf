package it.auties.protobuf.parser.type;

import it.auties.protobuf.base.ProtobufType;

import java.util.Locale;

public record ProtobufPrimitiveType(ProtobufType type) implements ProtobufTypeReference {
    public static ProtobufPrimitiveType of(ProtobufType protobufType){
        return new ProtobufPrimitiveType(protobufType);
    }

    public ProtobufPrimitiveType {
        if(type == ProtobufType.MESSAGE){
            throw new IllegalArgumentException("A primitive type cannot wrap a message");
        }
    }

    @Override
    public String name() {
        return type().name()
                .toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean primitive(){
        return true;
    }
}
