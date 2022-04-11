package it.auties.protobuf.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.auties.protobuf.util.VersionInfo;

public class ProtobufMapper extends ObjectMapper {
    public ProtobufMapper() {
        this(new ProtobufFactory());
    }

    public ProtobufMapper(ProtobufFactory f) {
        super(f);
    }

    protected ProtobufMapper(ProtobufMapper src) {
        super(src);
    }

    public static ProtobufMapperBuilder builder() {
        return new ProtobufMapperBuilder(new ProtobufMapper());
    }

    public static ProtobufMapperBuilder builder(ProtobufFactory streamFactory) {
        return new ProtobufMapperBuilder(new ProtobufMapper(streamFactory));
    }

    @Override
    public ProtobufMapper copy() {
        _checkInvalidCopy(ProtobufMapper.class);
        return new ProtobufMapper(this);
    }

    @Override
    public Version version() {
        return VersionInfo.current();
    }

    @Override
    public ProtobufFactory getFactory() {
        return (ProtobufFactory) _jsonFactory;
    }
}