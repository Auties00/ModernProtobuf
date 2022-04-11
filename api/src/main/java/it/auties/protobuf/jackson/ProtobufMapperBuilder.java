package it.auties.protobuf.jackson;

import com.fasterxml.jackson.databind.cfg.MapperBuilder;

public class ProtobufMapperBuilder extends MapperBuilder<ProtobufMapper, ProtobufMapperBuilder> {
    public ProtobufMapperBuilder(ProtobufMapper m) {
        super(m);
    }
}

