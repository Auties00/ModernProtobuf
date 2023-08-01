package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

public record ProtobufPrimitiveType(ProtobufType protobufType) implements ProtobufTypeReference {
    public static ProtobufPrimitiveType of(ProtobufType protobufType){
        return new ProtobufPrimitiveType(protobufType);
    }

    public ProtobufPrimitiveType {
        if(protobufType == ProtobufType.OBJECT){
            throw new IllegalArgumentException("A primitive protobufType cannot wrap a message");
        }
    }

    @Override
    public boolean isPrimitive(){
        return true;
    }
}
