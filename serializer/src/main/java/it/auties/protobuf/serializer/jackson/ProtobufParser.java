package it.auties.protobuf.serializer.jackson;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import it.auties.protobuf.base.ProtobufConverter;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.serializer.exception.ProtobufDeserializationException;
import it.auties.protobuf.serializer.util.ArrayInputStream;
import it.auties.protobuf.serializer.util.ProtobufField;
import it.auties.protobuf.serializer.util.ProtobufUtils;
import it.auties.protobuf.serializer.util.VersionInfo;
import it.auties.reflection.Reflection;
import lombok.NonNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.auties.protobuf.serializer.util.WireType.*;

class ProtobufParser extends ParserMinimalBase {
    private static final ConcurrentMap<Class<? extends ProtobufMessage>, ConcurrentMap<Integer, ProtobufField>> fieldsCache
            = new ConcurrentHashMap<>();

    private final IOContext ioContext;
    private ObjectCodec codec;
    private ArrayInputStream input;
    private ArrayInputStream packedInput;
    private Class<? extends ProtobufMessage> type;
    private boolean closed;
    private ProtobufField lastField;
    private int lastType;
    private Object lastValue;

    public ProtobufParser(IOContext ioContext, int parserFeatures, ObjectCodec codec, byte[] input) {
        super(parserFeatures);
        this.input = new ArrayInputStream(input);
        this.ioContext = ioContext;
        this.codec = codec;
    }

    @Override
    public ObjectCodec getCodec() {
        return codec;
    }

    @Override
    public void setCodec(ObjectCodec codec) {
        this.codec = codec;
    }

    @Override
    public Version version() {
        return VersionInfo.current();
    }

    @Override
    protected void _handleEOF() {

    }

    @Override
    public void close() {
        this.closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public JsonStreamContext getParsingContext() {
        return null;
    }

    @Override
    public JsonLocation getCurrentLocation() {
        return null;
    }

    @Override
    public void setSchema(FormatSchema schema) {
        if (schema == null) {
            return;
        }

        this.type = ((ProtobufSchema) schema).messageType();
        getFieldsOrCache(type);
    }

    private Map<Integer, ProtobufField> getFieldsOrCache(@NonNull Class<? extends ProtobufMessage> clazz) {
        var cached = fieldsCache.get(clazz);
        if (cached != null) {
            return cached;
        }

        var fields = createFields(clazz);
        if (clazz.getSuperclass() == null || !ProtobufMessage.isMessage(clazz.getSuperclass())) {
            fieldsCache.put(clazz, fields);
            return fields;
        }

        var superFields = getFieldsOrCache(clazz.getSuperclass().asSubclass(ProtobufMessage.class));
        superFields.forEach(fields::putIfAbsent);
        fieldsCache.put(clazz, fields);
        return fields;
    }

    private ConcurrentMap<Integer, ProtobufField> createFields(Class<? extends ProtobufMessage> type) {
        return Stream.of(type.getDeclaredFields(), type.getFields())
                .flatMap(Arrays::stream)
                .filter(ProtobufUtils::isProperty)
                .map(this::createField)
                .collect(Collectors.toConcurrentMap(ProtobufField::index, Function.identity()));
    }

    private ProtobufField createField(Field field) {
        var property = ProtobufUtils.getProperty(field);
        var name = ProtobufUtils.getFieldName(field);
        var type = ProtobufUtils.getJavaType(field, property);
        var conversion = requiresConversion(field, property);
        return new ProtobufField(name, type, conversion, property);
    }

    private boolean requiresConversion(Field field, ProtobufProperty property) {
        return property.implementation() != null
                && property.implementation() != ProtobufMessage.class
                && !property.repeated()
                && property.implementation() != field.getType();
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return schema instanceof ProtobufSchema;
    }

    @Override
    public JsonToken nextToken() {
        if (!hasCurrentToken()) {
            return super._currToken = JsonToken.START_OBJECT;
        }

        if (_currToken == JsonToken.FIELD_NAME) {
            return super._currToken = readValueAndToken();
        }

        if (packedInput != null) {
            if (packedInput.isAtEnd()) {
                this.packedInput = null;
                return super._currToken = JsonToken.END_ARRAY;
            }

            this.lastValue = lastType == 1 ? packedInput.readInt64() : packedInput.readInt32();
            return super._currToken = JsonToken.VALUE_NUMBER_INT;
        }

        var tag = input.readTag();
        if (tag == 0) {
            return super._currToken = JsonToken.END_OBJECT;
        }

        var index = tag >>> 3;
        if (index == 0) {
            throw new ProtobufDeserializationException("Cannot deserialize field with index %s inside %s: invalid field index"
                    .formatted(index, type.getName()));
        }

        // TODO: 20/07/2022 handle reserved fields
        this.lastField = findField(index);
        this.lastType = tag & 7;
        return super._currToken = JsonToken.FIELD_NAME;
    }

    private ProtobufField findField(int index) {
        try {
            return fieldsCache.get(type).get(index);
        } catch (NullPointerException exception) {
            throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: missing fields in cache(known: %s)"
                    .formatted(index, type.getName(), fieldsCache.keySet().stream().map(Class::getName).collect(Collectors.joining(","))));
        }
    }

    private JsonToken readValueAndToken() {
        return switch (lastType) {
            case WIRE_TYPE_VAR_INT -> {
                this.lastValue = input.readInt64();
                yield tokenOrNull(JsonToken.VALUE_NUMBER_INT);
            }

            case WIRE_TYPE_FIXED64 -> {
                this.lastValue = input.readFixed64();
                yield tokenOrNull(JsonToken.VALUE_NUMBER_INT);
            }

            case WIRE_TYPE_LENGTH_DELIMITED -> {
                this.lastValue = readDelimitedWithConversion();
                yield tokenOrNull(packedInput != null ? JsonToken.START_ARRAY : JsonToken.VALUE_EMBEDDED_OBJECT);
            }

            case WIRE_TYPE_EMBEDDED_MESSAGE -> {
                this.lastValue = readEmbeddedMessage(input.readBytes());
                yield tokenOrNull(JsonToken.VALUE_EMBEDDED_OBJECT);
            }

            case WIRE_TYPE_END_OBJECT -> {
                this.lastValue = null;
                yield tokenOrNull(JsonToken.END_OBJECT);
            }

            case WIRE_TYPE_FIXED32 -> {
                this.lastValue = input.readFixed32();
                yield tokenOrNull(JsonToken.VALUE_NUMBER_INT);
            }

            default ->
                    throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, invalid wire type: %s"
                            .formatted(lastField.index(), type.getName(), lastType));
        };
    }

    private JsonToken tokenOrNull(JsonToken token) {
        return lastField == null ? JsonToken.VALUE_NULL : token;
    }

    private Object readDelimitedWithConversion() {
        var delimited = readDelimited();
        if (lastField == null
                || lastField.messageType() == null
                || !lastField.requiresConversion()) {
            return delimited;
        }

        var converter = findConverter(delimited);
        Reflection.open(converter);
        try {
            return converter.invoke(null, delimited);
        } catch (ReflectiveOperationException exception) {
            throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: cannot invoke converter"
                    .formatted(lastField.index(), type.getName()), exception);
        }
    }

    private Method findConverter(Object argument) {
        return Arrays.stream(lastField.messageType().getMethods())
                .filter(method -> isValidConverter(method, argument))
                .findFirst()
                .orElseThrow(() -> new ProtobufDeserializationException("Cannot deserialize field %s inside %s: no converter found(required explicitly)"
                        .formatted(lastField.index(), type.getName())));
    }

    private boolean isValidConverter(Method method, Object argument) {
        return Modifier.isStatic(method.getModifiers())
                && method.isAnnotationPresent(ProtobufConverter.class)
                && method.getReturnType() == type
                && method.getParameters().length == 1
                && (argument == null || method.getParameters()[0].getType().isAssignableFrom(argument.getClass()));
    }

    private Object readDelimited() {
        var read = input.readBytes();
        if (lastField == null) {
            return read;
        }

        if (!lastField.packed()) {
            return switch (lastField.type()) {
                case BYTES -> read;
                case STRING -> new String(read, StandardCharsets.UTF_8);
                case MESSAGE -> readEmbeddedMessage(read);
                default ->
                        throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, type mismatch: expected bytes, string or message, got %s"
                                .formatted(lastField.index(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
            };
        }

        var int64 = lastField.type().isLong();
        if (!lastField.type().isInt() && !int64) {
            throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, type mismatch: expected scalar type, got %s(packed field)"
                    .formatted(lastField.index(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
        }

        this.lastType = int64 ? 1 : 5;
        this.packedInput = new ArrayInputStream(read);
        return null;
    }

    private Object readEmbeddedMessage(byte[] bytes) {
        if (lastField == null) {
            return bytes;
        }

        var lastField = this.lastField;
        var lastType = this.type;
        var lastInput = this.input;
        var lastToken = this.currentToken();
        var lastValue = this.lastValue;
        try {
            getFieldsOrCache(lastField.messageType());
            this.type = lastField.messageType();
            this.input = new ArrayInputStream(bytes);
            this._currToken = null;
            this.lastField = null;
            return readValueAs(lastField.messageType());
        } catch (Throwable exception) {
            throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: cannot decode embedded message with type %s"
                    .formatted(lastField.index(), lastType.getName(), lastField.messageType() == null ? "unknown" : lastField.messageType().getName()), exception);
        } finally {
            this.type = lastType;
            this.input = lastInput;
            this._currToken = lastToken;
            this.lastField = lastField;
            this.lastValue = lastValue;
        }
    }

    @Override
    public String getCurrentName() {
        return lastField == null ? "unknown" : lastField.name();
    }

    @Override
    public Object getCurrentValue() {
        return lastValue;
    }

    @Override
    public JsonLocation getTokenLocation() {
        return new JsonLocation(ioContext.contentReference(),
                input.position(),
                -1, -1, input.position());
    }

    @Override
    public void overrideCurrentName(String name) {
        throw new UnsupportedOperationException("Cannot override current name with %s".formatted(name));
    }

    @Override
    public String getText() {
        if (getCurrentValue() instanceof String string) {
            return string;
        }

        throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, type mismatch: expected String, got %s"
                .formatted(lastField.index(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
    }

    @Override
    public char[] getTextCharacters() {
        throw new UnsupportedOperationException("Text characters are not supported");
    }

    @Override
    public boolean hasTextCharacters() {
        return false;
    }

    @Override
    public int getTextLength() {
        var text = getText();
        return text != null ? text.length() : 0;
    }

    @Override
    public int getTextOffset() {
        return 0;
    }

    @Override
    public Number getNumberValue() {
        if (getCurrentValue() instanceof Number number) {
            return number;
        }

        throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, type mismatch: expected Number, got %s"
                .formatted(lastField.index(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
    }

    @Override
    public NumberType getNumberType() {
        return switch (lastField.type()) {
            case FLOAT -> NumberType.FLOAT;
            case DOUBLE -> NumberType.DOUBLE;
            case INT32, SINT32, UINT32, FIXED32, SFIXED32, BOOL -> NumberType.INT;
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> NumberType.LONG;
            default ->
                    throw new ProtobufDeserializationException("Cannot get number type for field %s inside %s for wire type %s and value %s, type mismatch: expected Number, got %s(%s)"
                            .formatted(lastField.index(), this.type.getName(), lastType, (lastValue == null ? "unknown" : lastValue.getClass().getName()), lastField.type().name(), _currToken.name()));
        };
    }

    @Override
    public int getIntValue() {
        return getNumberValue().intValue();
    }

    @Override
    public long getLongValue() {
        return getNumberValue().longValue();
    }

    @Override
    public BigInteger getBigIntegerValue() throws IOException {
        return new BigInteger(getBinaryValue());
    }

    @Override
    public float getFloatValue() {
        return Float.intBitsToFloat(getIntValue());
    }

    @Override
    public double getDoubleValue() {
        return Double.longBitsToDouble(getLongValue());
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException {
        return new BigDecimal(getBigIntegerValue().longValue());
    }

    @Override
    public Object getEmbeddedObject() {
        return getCurrentValue();
    }

    @Override
    public byte[] getBinaryValue(Base64Variant decoder) {
        return switch (getCurrentValue()) {
            case String string -> decoder.decode(string);
            case byte[] bytes -> bytes;
            default ->
                    throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, type mismatch: expected String or byte[], got %s"
                            .formatted(lastField.index(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
        };
    }
}