package it.auties.protobuf.api.jackson;

import com.fasterxml.jackson.databind.cfg.MapperBuilder;

public class ProtobufMapperBuilder extends MapperBuilder<ProtobufMapper, ProtobufMapperBuilder> {
    public ProtobufMapperBuilder() {
        this(new ProtobufMapper());
    }

    public ProtobufMapperBuilder(ProtobufMapper mapper) {
        super(mapper);
    }
}
