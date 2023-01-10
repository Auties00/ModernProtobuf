package it.auties.protobuf.serialization.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.serialization.exception.ProtobufDeserializationException;
import java.io.IOException;

public class ProtobufMapper extends ObjectMapper {
    public ProtobufMapper() {
        this(new ProtobufFactory());
    }

    private ProtobufMapper(ProtobufFactory factory) {
        super(factory);
        registerModule(new RepeatedCollectionModule());
    }

    protected ProtobufMapper(ProtobufMapper src) {
        super(src);
    }

    public static ProtobufMapperBuilder builder() {
        return new ProtobufMapperBuilder();
    }

    public <T extends ProtobufMessage> T readMessage(byte[] src, Class<T> valueType) throws IOException {
        return reader(ProtobufSchema.of(valueType))
                .readValue(src, valueType);
    }

    @Override
    protected Object _readMapAndClose(JsonParser parser, JavaType valueType) throws IOException {
        if (!ProtobufMessage.isMessage(valueType.getRawClass())) {
            throw new ProtobufDeserializationException("Cannot deserialize message, invalid type: expected ProtobufMessage, got %s"
                    .formatted(valueType.getRawClass().getName()));
        }

        if (parser.getSchema() == null) {
            parser.setSchema(ProtobufSchema.of(valueType.getRawClass().asSubclass(ProtobufMessage.class)));
        }

        return super._readMapAndClose(parser, valueType);
    }

    @Override
    public ProtobufMapper copy() {
        _checkInvalidCopy(ProtobufMapper.class);
        return new ProtobufMapper(this);
    }

    @Override
    public DeserializationConfig getDeserializationConfig() { // These options are set to be compliant with the proto spec
        return _deserializationConfig.with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public Version version() {
        return ProtobufVersionInfo.current();
    }
}