package it.auties.protobuf.json;

import com.fasterxml.jackson.databind.module.SimpleModule;

public class ByteStringModule extends SimpleModule {
    public ByteStringModule(){
        addDeserializer(byte[].class, new ByteStringDeserializer());
    }
}
