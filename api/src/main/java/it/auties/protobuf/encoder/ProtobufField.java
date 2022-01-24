package it.auties.protobuf.encoder;

import java.util.Objects;

record ProtobufField(int index, String type, Object value, boolean required) {
    public boolean valid(){
        if(required){
            Objects.requireNonNull(value, "Cannot encode object: missing mandatory field");
        }

        return value != null
                && (!(value instanceof Number number) || number.floatValue() != 0F);
    }

    public String type(){
        return type.replace("[packed]", "")
                .trim();
    }
}
