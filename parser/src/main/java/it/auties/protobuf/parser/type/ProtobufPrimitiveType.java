package it.auties.protobuf.parser.type;

import it.auties.protobuf.base.ProtobufType;

public record ProtobufPrimitiveType(ProtobufType protobufType) implements ProtobufTypeReference {
    public static ProtobufPrimitiveType of(ProtobufType protobufType){
        return new ProtobufPrimitiveType(protobufType);
    }

    public ProtobufPrimitiveType {
        if(protobufType == ProtobufType.MESSAGE){
            throw new IllegalArgumentException("A primitive protobufType cannot wrap a message");
        }
    }

    @Override
    public boolean primitive(){
        return true;
    }
}
