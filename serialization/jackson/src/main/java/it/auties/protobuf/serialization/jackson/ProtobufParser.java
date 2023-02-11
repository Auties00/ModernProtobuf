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
import it.auties.protobuf.serialization.exception.ProtobufDeserializationException;
import it.auties.protobuf.serialization.stream.ArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

class ProtobufParser extends ParserMinimalBase {
    private final IOContext ioContext;
    private ObjectCodec codec;
    private ArrayInputStream input;
    private ArrayInputStream packedInput;
    private Class<? extends ProtobufMessage> type;
    private boolean closed;

    private ProtobufField lastField;
    private int lastType;
    private int lastValueInt;
    private long lastValueLong;
    private Object lastValueObject;

    public ProtobufParser(IOContext ioContext, int parserFeatures, ObjectCodec codec, byte[] input) {
        super(parserFeatures);
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
        ProtobufFieldCache.cacheFields(type);
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return schema instanceof ProtobufSchema;
    }

    @Override
    public JsonToken nextToken() {
        if (_currToken == null) {
            return _currToken = JsonToken.START_OBJECT;
        }

        if (_currToken == JsonToken.FIELD_NAME) {
            return _currToken = readValueAndToken();
        }

        if (packedInput != null) {
            return _currToken = readPackedValue();
        }

        var tag = input.readTag();
        if (tag == 0) {
            return _currToken = JsonToken.END_OBJECT;
        }

        this.lastType = tag & 7;
        this.lastField = ProtobufFieldCache.getField(type, tag >>> 3);
        return _currToken = JsonToken.FIELD_NAME;
    }

    private JsonToken readPackedValue() {
        if (packedInput.isAtEnd()) {
            this.packedInput = null;
            return JsonToken.END_ARRAY;
        }

        if (lastType == WIRE_TYPE_FIXED64) {
            this.lastValueLong = packedInput.readInt64();
            return JsonToken.VALUE_NUMBER_INT;
        }

        this.lastValueInt = packedInput.readInt32();
        return JsonToken.VALUE_NUMBER_INT;
    }

    private JsonToken readValueAndToken() {
        return switch (lastType) {
            case WIRE_TYPE_VAR_INT -> {
                saveNumber(input.readInt64());
                yield tokenOrNull(JsonToken.VALUE_NUMBER_INT);
            }
            case WIRE_TYPE_FIXED64 -> {
                saveNumber(input.readFixed64());
                yield tokenOrNull(JsonToken.VALUE_NUMBER_INT);
            }
            case WIRE_TYPE_FIXED32 -> {
                saveNumber(input.readFixed32());
                yield tokenOrNull(JsonToken.VALUE_NUMBER_INT);
            }
            case WIRE_TYPE_LENGTH_DELIMITED -> tokenOrNull(readDelimitedWithConversion());
            case WIRE_TYPE_EMBEDDED_MESSAGE -> {
                readEmbeddedMessage(input.readBytes());
                yield tokenOrNull(JsonToken.VALUE_EMBEDDED_OBJECT);
            }
            case WIRE_TYPE_END_OBJECT -> tokenOrNull(JsonToken.END_OBJECT);
            default ->
                    throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: invalid wire type %s"
                            .formatted(lastFieldIndex(), type.getName(), lastType));
        };
    }

    private void saveNumber(long value){
        switch (lastField.type()){
            case INT32, SINT32, UINT32, FIXED32, SFIXED32, FLOAT, BOOL, MESSAGE -> this.lastValueInt = (int) value;
            case INT64, SINT64, UINT64, FIXED64, SFIXED64, DOUBLE -> this.lastValueLong = value;
            default ->
                throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: invalid number type: %s"
                    .formatted(lastFieldIndex(), type.getName(), lastType));
        }
    }

    private JsonToken tokenOrNull(JsonToken token) {
        return lastField == null ? JsonToken.VALUE_NULL : token;
    }

    private JsonToken readDelimitedWithConversion() {
        var token = readDelimited();
        if (lastField == null || lastField.messageType() == null || !lastField.convert()) {
            return token;
        }

        try {
            var converter = findConverter();
            converter.setAccessible(true);
            this.lastValueObject = converter.invoke(null, lastValueObject);
            return JsonToken.VALUE_EMBEDDED_OBJECT;
        } catch (ReflectiveOperationException exception) {
            throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: cannot invoke converter"
                    .formatted(lastFieldIndex(), type.getName()), exception);
        }
    }

    private Method findConverter() {
        return Arrays.stream(lastField.messageType().getMethods())
                .filter(this::isValidConverter)
                .findFirst()
                .orElseThrow(() -> new ProtobufDeserializationException("Cannot deserialize field %s with index %s in %s: no converter found from %s to %s"
                        .formatted(lastField.name(), lastFieldIndex(), type.getName(), lastValueObject.getClass().getName(), lastField.messageType().getName())));
    }

    private boolean isValidConverter(Method method) {
        return Modifier.isStatic(method.getModifiers())
                && method.isAnnotationPresent(ProtobufConverter.class)
                && method.getReturnType() == lastField.messageType()
                && method.getParameters().length >= 1
                && (lastValueObject == null || method.getParameters()[0].getType().isAssignableFrom(lastValueObject.getClass()));
    }

    private JsonToken readDelimited() {
        var read = input.readBytes();
        return lastField == null ? readUnknownField(read)
            : lastField.packed() ? readPackedField(read) : readGenericField(read);
    }

    private JsonToken readPackedField(byte[] read) {
        this.lastType = switch (lastField.type()) {
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> WIRE_TYPE_FIXED64;
            case INT32, SINT32, UINT32, FIXED32, SFIXED32 -> WIRE_TYPE_FIXED32;
            default -> throw new ProtobufDeserializationException(
                "Cannot deserialize field %s inside %s: type mismatch(expected scalar type for packed field, got %s)"
                    .formatted(lastFieldIndex(), type.getName(),
                        (getCurrentValue() == null ? null
                            : getCurrentValue().getClass().getSimpleName())));
        };
        this.packedInput = new ArrayInputStream(read);
        return JsonToken.START_ARRAY;
    }

    private JsonToken readGenericField(byte[] read) {
        return switch (lastField.type()) {
            case BYTES -> {
                this.lastValueObject = read;
                yield JsonToken.VALUE_EMBEDDED_OBJECT;
            }
            case STRING -> {
                this.lastValueObject = new String(read, StandardCharsets.UTF_8);
                yield JsonToken.VALUE_STRING;
            }
            case MESSAGE -> {
                readEmbeddedMessage(read);
                yield JsonToken.VALUE_EMBEDDED_OBJECT;
            }
            default -> throw new ProtobufDeserializationException(
                "Cannot deserialize field %s inside %s, type mismatch: expected bytes, string or message, got %s"
                    .formatted(
                        lastFieldIndex(), type.getName(), (getCurrentValue() == null ? null
                            : getCurrentValue().getClass().getSimpleName())));
        };
    }

    private JsonToken readUnknownField(byte[] read) {
        this.lastValueObject = read;
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }

    private void readEmbeddedMessage(byte[] bytes) {
        if (lastField == null) {
            this.lastValueObject = bytes;
            return;
        }

        var lastField = this.lastField;
        var lastType = this.type;
        var lastInput = this.input;
        var lastToken = super._currToken;
        var lastValueInt = this.lastValueInt;
        var lastValueLong = this.lastValueLong;
        try {
            ProtobufFieldCache.cacheFields(lastField.messageType());
            this.type = lastField.messageType();
            this.input = new ArrayInputStream(bytes);
            this._currToken = null;
            this.lastField = null;
            this.lastValueInt = 0;
            this.lastValueLong = 0;
            this.lastValueObject = null;
            this.lastValueObject = readValueAs(lastField.messageType());
        } catch (Throwable exception) {
            throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: cannot decode embedded message with type %s"
                .formatted(lastField.index(), lastType.getName(), lastField.messageType() == null ? "unknown" : lastField.messageType().getName()), exception);
        } finally {
            this.type = lastType;
            this.input = lastInput;
            this._currToken = lastToken;
            this.lastField = lastField;
            this.lastValueInt = lastValueInt;
            this.lastValueLong = lastValueLong;
        }
    }

    @Override
    public String getCurrentName() {
        return lastField == null ? "unknown" : lastField.name();
    }

    @Override
    public Object getCurrentValue() {
        return switch (lastField.type()){
            case MESSAGE, BYTES, STRING -> lastValueObject;
            case FLOAT -> Float.intBitsToFloat(lastValueInt);
            case DOUBLE -> Double.longBitsToDouble(lastValueLong);
            case BOOL, INT32, FIXED32, UINT32, SFIXED32, SINT32 -> lastValueInt;
            case INT64, SINT64, FIXED64, UINT64, SFIXED64 -> lastValueLong;
        };
    }

    @Override
    public JsonLocation getTokenLocation() {
        return new JsonLocation(ioContext.contentReference(),
                input.position(),
                -1, -1, input.position());
    }

    @Override
    public String getText() {
        return (String) lastValueObject;
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
        return switch (lastField.type()){
            case INT32, SINT32, UINT32, FIXED32, SFIXED32, BOOL, MESSAGE -> lastValueInt;
            case INT64, SINT64, UINT64, FIXED64, SFIXED64-> lastValueLong;
            case FLOAT -> getFloatValue();
            case DOUBLE -> getDoubleValue();
            default ->
                throw new ProtobufDeserializationException("Cannot get number for field %s inside %s for wire type %s: type mismatch"
                    .formatted(lastFieldIndex(), this.type.getName(), lastType));
        };
    }

    @Override
    public NumberType getNumberType() {
        return switch (lastField.type()){
            case INT32, SINT32, UINT32, FIXED32, SFIXED32, BOOL, MESSAGE -> NumberType.INT;
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> NumberType.LONG;
            case FLOAT -> NumberType.FLOAT;
            case DOUBLE -> NumberType.DOUBLE;
            default ->
                throw new ProtobufDeserializationException("Cannot get number type for field %s inside %s for wire type %s: type mismatch"
                    .formatted(lastFieldIndex(), this.type.getName(), lastType));
        };
    }

    @Override
    public int getIntValue() {
        return lastValueInt;
    }

    @Override
    public long getLongValue() {
        return lastValueLong;
    }

    @Override
    public float getFloatValue() {
        return Float.intBitsToFloat(lastValueInt);
    }

    @Override
    public double getDoubleValue() {
        return Double.longBitsToDouble(lastValueLong);
    }

    @Override
    public Object getEmbeddedObject() {
        return lastValueObject;
    }

    @Override
    public byte[] getBinaryValue(Base64Variant decoder) {
        throw new UnsupportedOperationException("Binary value is not supported");
    }

    @Override
    public BigInteger getBigIntegerValue() {
        throw new UnsupportedOperationException("Big integers are not supported");
    }

    @Override
    public BigDecimal getDecimalValue() {
        throw new UnsupportedOperationException("Big decimals are not supported");
    }

    @Override
    public void overrideCurrentName(String name) {
        throw new UnsupportedOperationException("Cannot override current name with %s".formatted(name));
    }

    @Override
    public char[] getTextCharacters() {
        throw new UnsupportedOperationException("Text characters are not supported");
    }

    @Override
    public boolean hasTextCharacters() {
        return false;
    }

    protected ProtobufField lastField() {
        return lastField;
    }

    private int lastFieldIndex() {
        return lastField == null ? 0 : lastField.index();
    }
}