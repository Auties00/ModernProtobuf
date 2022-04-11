package it.auties.protobuf.jackson;

import com.fasterxml.jackson.core.TSFBuilder;

public class ProtobufFactoryBuilder extends TSFBuilder<ProtobufFactory, ProtobufFactoryBuilder> {
    public ProtobufFactoryBuilder() {
        super();
    }

    public ProtobufFactoryBuilder(ProtobufFactory base) {
        super(base);
    }

    @Override
    public ProtobufFactory build() {
        return new ProtobufFactory(this);
    }
}