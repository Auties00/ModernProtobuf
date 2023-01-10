package it.auties.protobuf.serialization.jackson;

import static it.auties.protobuf.serialization.model.WireType.WIRE_TYPE_EMBEDDED_MESSAGE;
import static it.auties.protobuf.serialization.model.WireType.WIRE_TYPE_END_OBJECT;
import static it.auties.protobuf.serialization.model.WireType.WIRE_TYPE_FIXED32;
import static it.auties.protobuf.serialization.model.WireType.WIRE_TYPE_FIXED64;
import static it.auties.protobuf.serialization.model.WireType.WIRE_TYPE_LENGTH_DELIMITED;
import static it.auties.protobuf.serialization.model.WireType.WIRE_TYPE_VAR_INT;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import it.auties.protobuf.base.ProtobufConverter;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufReserved;
import it.auties.protobuf.serialization.exception.ProtobufDeserializationException;
import it.auties.protobuf.serialization.stream.ArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;

class ProtobufParser extends ParserMinimalBase {
    private static final ConcurrentMap<Class<?>, ConcurrentMap<Integer, ProtobufField>> fieldsCache
            = new ConcurrentHashMap<>();

    private long id;
    private final IOContext ioContext;
    private ObjectCodec codec;
    private ArrayInputStream input;
    private ArrayInputStream packedInput;
    private Class<? extends ProtobufMessage> type;
    private ProtobufReserved reservedType;
    private boolean closed;
    private ProtobufField lastField;
    private int lastType;
    private Object lastValue;

    public ProtobufParser(IOContext ioContext, int parserFeatures, ObjectCodec codec, byte[] input) {
        super(parserFeatures);
        this.id = ThreadLocalRandom.current().nextLong();
        this.ioContext = ioContext;
        this.input = new ArrayInputStream(input);
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
        return ProtobufVersionInfo.current();
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
        this.reservedType = type.getAnnotation(ProtobufReserved.class);
        getFieldsOrCache(type);
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

            this.lastValue = lastType == WIRE_TYPE_FIXED64 ? packedInput.readInt64() : packedInput.readInt32();
            return super._currToken = JsonToken.VALUE_NUMBER_INT;
        }

        var tag = input.readTag();
        if (tag == 0) {
            this.lastValue = null;
            return super._currToken = JsonToken.END_OBJECT;
        }

        var index = tag >>> 3;
        if (index == 0) {
            throw new ProtobufDeserializationException("Cannot deserialize field with index %s inside %s: invalid field index"
                    .formatted(index, type.getName()));
        }

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
            case WIRE_TYPE_VAR_INT -> readNumber(input.readInt64());

            case WIRE_TYPE_FIXED64 -> readNumber(input.readFixed64());

            case WIRE_TYPE_FIXED32 -> readNumber(input.readFixed32());

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
                yield  tokenOrNull(JsonToken.END_OBJECT);
            }

            default ->
                    throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, invalid wire type: %s"
                            .formatted(lastField.index(), type.getName(), lastType));
        };
    }

    private JsonToken readNumber(long input) {
        var isFloat = false;
        if (lastField.type().isLong()) {
            this.lastValue = input;
        } else if (lastField.type().isFloat()) {
            this.lastValue = Float.intBitsToFloat((int) input);
            isFloat = true;
        } else if (lastField.type().isDouble()) {
            this.lastValue = Double.longBitsToDouble(input);
            isFloat = true;
        } else {
            this.lastValue = (int) input;
        }

        return tokenOrNull(isFloat ? JsonToken.VALUE_NUMBER_FLOAT : JsonToken.VALUE_NUMBER_INT);
    }

    private JsonToken tokenOrNull(JsonToken token) {
        return lastField == null ? JsonToken.VALUE_NULL : token;
    }

    private Object readDelimitedWithConversion() {
        var delimited = readDelimited();
        if (lastField == null
                || lastField.messageType() == null
                || !lastField.convert()) {
            return delimited;
        }

        var converter = findConverter(delimited);
        try {
            converter.setAccessible(true);
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
                .orElseThrow(() -> new ProtobufDeserializationException("Cannot deserialize field %s(%s) in %s: no converter found from %s to %s"
                        .formatted(lastField.name(), lastField.index(), type.getName(), argument.getClass().getName(), lastField.messageType().getName())));
    }

    private boolean isValidConverter(Method method, Object argument) {
        return Modifier.isStatic(method.getModifiers())
                && method.isAnnotationPresent(ProtobufConverter.class)
                && method.getReturnType() == lastField.messageType()
                && method.getParameters().length >= 1
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

        this.lastType = int64 ? WIRE_TYPE_FIXED64 : WIRE_TYPE_FIXED32;
        this.packedInput = new ArrayInputStream(read);
        return null;
    }

    private Object readEmbeddedMessage(byte[] bytes) {
        if (lastField == null) {
            return bytes;
        }

        var lastField = this.lastField;
        var lastType = this.type;
        var lastReservedType = this.reservedType;
        var lastInput = this.input;
        var lastToken = this.currentToken();
        var lastValue = this.lastValue;
        var lastId = this.id;
        try {
            getFieldsOrCache(lastField.messageType());
            this.id = ThreadLocalRandom.current().nextLong();
            this.type = lastField.messageType();
            this.reservedType = type.getAnnotation(ProtobufReserved.class);
            this.input = new ArrayInputStream(bytes);
            this._currToken = null;
            this.lastField = null;
            this.lastValue = null;
            return readValueAs(lastField.messageType());
        } catch (Throwable exception) {
            throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: cannot decode embedded message with type %s"
                    .formatted(lastField.index(), lastType.getName(), lastField.messageType() == null ? "unknown" : lastField.messageType().getName()), exception);
        } finally {
            this.id = lastId;
            this.type = lastType;
            this.reservedType = lastReservedType;
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
        try {
            return (String) lastValue;
        }catch (ClassCastException exception){
            throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, type mismatch: expected String, got %s"
                    .formatted(lastField.index(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
        }
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
        try {
            return (Number) lastValue;
        }catch (ClassCastException exception){
            throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, type mismatch: expected Number, got %s"
                    .formatted(lastField.index(), type.getName(), getCurrentValue().getClass().getSimpleName()));
        }
    }

    @Override
    public NumberType getNumberType() {
        if(lastField.type().isInt() || lastField.type().isBool()){
            return NumberType.INT;
        }

        if(lastField.type().isLong()){
            return NumberType.LONG;
        }

        if(lastField.type().isFloat()){
            return NumberType.FLOAT;
        }

        if(lastField.type().isDouble()){
            return NumberType.DOUBLE;
        }

        throw new ProtobufDeserializationException("Cannot get number type for field %s inside %s for wire type %s and value %s, type mismatch: expected Number, got %s(%s)"
                .formatted(lastField.index(), this.type.getName(), lastType, (lastValue == null ? "unknown" : lastValue.getClass().getName()), lastField.type().name(), _currToken.name()));
    }

    @Override
    public int getIntValue() {
        return (Integer) lastValue;
    }

    @Override
    public long getLongValue() {
        return (Long) lastValue;
    }

    @Override
    public float getFloatValue() {
        return (Float) lastValue;
    }

    @Override
    public double getDoubleValue() {
        return (Double) lastValue;
    }

    @Override
    public Object getEmbeddedObject() {
        return lastValue;
    }

    @Override
    public byte[] getBinaryValue(Base64Variant decoder) {
        return switch (lastValue) {
            case String string -> decoder.decode(string);
            case byte[] bytes -> bytes;
            default -> throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s, type mismatch: expected String or byte[], got %s"
                    .formatted(lastField.index(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
        };
    }

    @Override
    public BigInteger getBigIntegerValue() {
        throw new UnsupportedOperationException("Big integers are not supported");
    }

    @Override
    public BigDecimal getDecimalValue() {
        throw new UnsupportedOperationException("Big decimals are not supported");
    }

    private Map<Integer, ProtobufField> getFieldsOrCache(@NonNull Class<?> clazz) {
        var cached = fieldsCache.get(clazz);
        if (cached != null) {
            return cached;
        }

        var fields = createFields(clazz);
        if (clazz.getSuperclass() == null || !ProtobufMessage.isMessage(clazz.getSuperclass())) {
            fieldsCache.put(clazz, fields);
            return fields;
        }

        fields.putAll(getFieldsOrCache(clazz.getSuperclass()));
        fieldsCache.put(clazz, fields);
        return fields;
    }

    private ConcurrentMap<Integer, ProtobufField> createFields(Class<?> type) {
        return Stream.of(type.getDeclaredFields(), type.getFields())
                .flatMap(Arrays::stream)
                .filter(ProtobufField::isProperty)
                .map(ProtobufField::ofRead)
                .collect(Collectors.toConcurrentMap(ProtobufField::index, Function.identity()));
    }

    public long id() {
        return id;
    }

    protected ProtobufField lastField() {
        return lastField;
    }

    protected Class<? extends ProtobufMessage> type() {
        return type;
    }
}