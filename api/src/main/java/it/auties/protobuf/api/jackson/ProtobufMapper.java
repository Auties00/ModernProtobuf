package it.auties.protobuf.api.jackson;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import it.auties.protobuf.api.util.VersionInfo;

public class ProtobufMapper extends ObjectMapper {
    public ProtobufMapper() {
        this(new ProtobufFactory());
    }

    private ProtobufMapper(ProtobufFactory factory) {
        super(factory);
    }

    protected ProtobufMapper(ProtobufMapper src) {
        super(src);
    }

    public static ProtobufMapperBuilder builder() {
        return new ProtobufMapperBuilder();
    }

    @Override
    protected ObjectReader _newReader(DeserializationConfig config) {
        return super._newReader(configureOptions(config));
    }

    @Override
    protected ObjectReader _newReader(DeserializationConfig config, JavaType valueType, Object valueToUpdate, FormatSchema schema, InjectableValues injectableValues) {
        return super._newReader(configureOptions(config), valueType, valueToUpdate, schema, injectableValues);
    }

    private DeserializationConfig configureOptions(DeserializationConfig config) {
        return config.withFeatures(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
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
}