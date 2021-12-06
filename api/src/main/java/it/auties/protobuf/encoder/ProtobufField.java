package it.auties.protobuf.encoder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;

record ProtobufField(JsonProperty property, JsonPropertyDescription description, Object value) {
    public boolean valid(){
        if(property.required()){
            Objects.requireNonNull(value, "Cannot encode object: missing mandatory field");
        }

        return value != null && (!(value instanceof Number number) || number.floatValue() != 0F);
    }

}
