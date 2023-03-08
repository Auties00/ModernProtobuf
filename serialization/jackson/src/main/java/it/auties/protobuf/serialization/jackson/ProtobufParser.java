package it.auties.protobuf.serialization.jackson;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import it.auties.protobuf.base.ProtobufConverter;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.serialization.exception.ProtobufDeserializationException;
import it.auties.protobuf.serialization.stream.ArrayInputStream;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static it.auties.protobuf.serialization.model.WireType.*;

class ProtobufParser extends ParserMinimalBase {
    private final IOContext ioContext;
    private ObjectCodec codec;
    private ArrayInputStream input;
    private ArrayInputStream packedInput;
    private Class<? extends ProtobufMessage> type;
    private boolean closed;

    private ProtobufField lastField;
    private Object lastValue;
    private int lastType;
    private int lastValueInt;
    private long lastValueLong;
    private String lastValueString;
    private float lastValueFloat;
    private double lastValueDouble;
    private boolean lastValueBool;
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
    protected void _handleEOF() {}

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
        } else if (_currToken == JsonToken.FIELD_NAME) {
            var token = readFieldContent();
            if (lastField != null && lastField.messageType() != null && lastField.convert()) {
                try {
                    var converter = findConverter();
                    converter.setAccessible(true);
                    this.lastValueObject = converter.invoke(null, lastValue);
                    return _currToken = JsonToken.VALUE_EMBEDDED_OBJECT;
                } catch (ReflectiveOperationException exception) {
                    throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: cannot invoke converter"
                            .formatted(lastField.index(), type.getName()), exception);
                }
            }
            return _currToken = token;
        } else if (packedInput != null) {
            if (packedInput.isAtEnd()) {
                this.packedInput = null;
                return _currToken = JsonToken.END_ARRAY;
            } else if (lastType == WIRE_TYPE_FIXED64) {
                this.lastValueLong = packedInput.readInt64();
                this.lastValue = lastValueLong;
                return _currToken = JsonToken.VALUE_NUMBER_INT;
            } else {
                this.lastValueInt = packedInput.readInt32();
                this.lastValue = lastValueInt;
                return _currToken = JsonToken.VALUE_NUMBER_INT;
            }
        } else {
            var tag = input.readTag();
            if (tag == 0) {
                return _currToken = JsonToken.END_OBJECT;
            }

            this.lastType = tag & 7;
            this.lastField = ProtobufFieldCache.getField(type, tag >>> 3);
            return _currToken = JsonToken.FIELD_NAME;
        }
    }
    
    private JsonToken readFieldContent(){
        return switch (lastType) {
            case WIRE_TYPE_VAR_INT -> {
                var value = input.readInt64();
                if(lastField == null) {
                    yield JsonToken.VALUE_NULL;
                } else {
                    yield switch (lastField.type()) {
                        case INT32, SINT32, UINT32, MESSAGE -> {
                            this.lastValueInt = (int) value;
                            this.lastValue = lastValueInt;
                            yield JsonToken.VALUE_NUMBER_INT;
                        }
                        case INT64, SINT64, UINT64 -> {
                            this.lastValueLong = value;
                            this.lastValue = lastValueLong;
                            yield JsonToken.VALUE_NUMBER_INT;
                        }
                        case BOOL -> {
                            var result = value == 1;
                            this.lastValueBool = result;
                            this.lastValue = lastValueBool;
                            yield result ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
                        }
                        default -> throw new ProtobufDeserializationException(
                                "Cannot deserialize field %s inside %s: type mismatch(expected scalar type, got %s)"
                                        .formatted(lastFieldIndex(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
                    };
                }
            }
            case WIRE_TYPE_FIXED64 -> {
                var value = input.readFixed64();
                if(lastField == null) {
                    yield JsonToken.VALUE_NULL;
                }else if(lastField.type() == ProtobufType.DOUBLE){
                    this.lastValueDouble = Double.longBitsToDouble(value);
                    this.lastValue = lastValueDouble;
                    yield JsonToken.VALUE_NUMBER_FLOAT;
                }else {
                    this.lastValueLong = value;
                    this.lastValue = lastValueLong;
                    yield JsonToken.VALUE_NUMBER_INT;
                }
            }
            case WIRE_TYPE_LENGTH_DELIMITED -> {
                var read = input.readBytes();
                if(lastField == null) {
                    yield JsonToken.VALUE_NULL;
                } else if(lastField.packed()) {
                    this.lastType = switch (lastField.type()) {
                        case FIXED32, SFIXED32, FLOAT -> WIRE_TYPE_FIXED32;
                        case INT32, SINT32, UINT32, INT64, SINT64, UINT64 -> WIRE_TYPE_VAR_INT;
                        case FIXED64, SFIXED64, DOUBLE -> WIRE_TYPE_FIXED64;
                        default -> throw new ProtobufDeserializationException(
                                "Cannot deserialize field %s inside %s: type mismatch(expected scalar type for packed field, got %s)"
                                        .formatted(lastFieldIndex(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
                    };
                    this.packedInput = new ArrayInputStream(read);
                    yield JsonToken.START_ARRAY;
                } else {
                    yield switch (lastField.type()) {
                        case BYTES -> {
                            this.lastValueObject = read;
                            this.lastValue = lastValueObject;
                            yield JsonToken.VALUE_EMBEDDED_OBJECT;
                        }
                        case STRING -> {
                            this.lastValueString = new String(read, StandardCharsets.UTF_8);
                            this.lastValue = lastValueString;
                            yield JsonToken.VALUE_STRING;
                        }
                        case MESSAGE -> {
                            this.lastValueObject = readEmbeddedMessage(read);
                            this.lastValue = lastValueObject;
                            yield JsonToken.VALUE_EMBEDDED_OBJECT;
                        }
                        default -> throw new ProtobufDeserializationException(
                                "Cannot deserialize field %s inside %s, type mismatch: expected bytes, string or message, got %s"
                                        .formatted(lastFieldIndex(), type.getName(), (getCurrentValue() == null ? null : getCurrentValue().getClass().getSimpleName())));
                    };
                }
            }
            case WIRE_TYPE_EMBEDDED_MESSAGE -> {
                var read = input.readBytes();
                if(lastField == null) {
                    yield JsonToken.VALUE_NULL;
                } else {
                    this.lastValueObject = readEmbeddedMessage(read);
                    this.lastValue = lastValueObject;
                    yield JsonToken.VALUE_EMBEDDED_OBJECT;
                }
            }
            case WIRE_TYPE_FIXED32 -> {
                var read = input.readFixed32();
                if(lastField == null){
                    yield JsonToken.VALUE_NULL;
                }else if (lastField.type() == ProtobufType.FLOAT) {
                    this.lastValueFloat = Float.intBitsToFloat(read);
                    this.lastValue = lastValueFloat;
                    yield JsonToken.VALUE_NUMBER_FLOAT;
                } else {
                    this.lastValueInt = read;
                    this.lastValue = lastValueInt;
                    yield JsonToken.VALUE_NUMBER_INT;
                }
            }
            case WIRE_TYPE_END_OBJECT -> JsonToken.END_OBJECT;
            default -> throw new ProtobufDeserializationException("Cannot deserialize field %s inside %s: invalid wire type %s"
                    .formatted(lastFieldIndex(), type.getName(), lastType));
        };
    }

    private Method findConverter() {
        return Arrays.stream(lastField.messageType().getMethods())
                .filter(this::isValidConverter)
                .findFirst()
                .orElseThrow(() -> new ProtobufDeserializationException("Cannot deserialize field %s(%s) in %s: no converter found from %s to %s"
                        .formatted(lastField.name(), lastField.index(), type.getName(), lastValueObject.getClass().getName(), lastField.messageType().getName())));
    }

    private boolean isValidConverter(Method method) {
        return Modifier.isStatic(method.getModifiers())
                && method.isAnnotationPresent(ProtobufConverter.class)
                && method.getReturnType() == lastField.messageType()
                && method.getParameters().length >= 1
                && (lastValueObject == null || method.getParameters()[0].getType().isAssignableFrom(lastValueObject.getClass()));
    }

    private Object readEmbeddedMessage(byte[] bytes) {
        var lastField = this.lastField;
        var lastType = this.type;
        var lastInput = this.input;
        var lastToken = super._currToken;
        var lastValueInt = this.lastValueInt;
        var lastValueLong = this.lastValueLong;
        var lastValueString = this.lastValueString;
        var lastValueFloat = this.lastValueFloat;
        var lastValueDouble = this.lastValueDouble;
        var lastValueBool = this.lastValueBool;
        var lastValue = this.lastValue;
        try {
            ProtobufFieldCache.cacheFields(lastField.messageType());
            this.type = lastField.messageType();
            this.input = new ArrayInputStream(bytes);
            this._currToken = null;
            this.lastField = null;
            this.lastValueInt = 0;
            this.lastValueLong = 0;
            this.lastValueString = null;
            this.lastValueFloat = 0;
            this.lastValueDouble = 0;
            this.lastValueBool = false;
            this.lastValueObject = null;
            this.lastValue = null;
            return readValueAs(lastField.messageType());
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
            this.lastValueString = lastValueString;
            this.lastValueFloat = lastValueFloat;
            this.lastValueDouble = lastValueDouble;
            this.lastValueBool = lastValueBool;
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
        return new JsonLocation(ioContext.contentReference(), input.position(), -1, -1, input.position());
    }

    @Override
    public String getText() {
        return lastValueString;
    }

    @Override
    public int getTextLength() {
        return lastValueString != null ? lastValueString.length() : 0;
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
            case FLOAT -> lastValueFloat;
            case DOUBLE -> lastValueDouble;
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
        return lastValueFloat;
    }

    @Override
    public double getDoubleValue() {
        return lastValueDouble;
    }

    @Override
    public boolean getBooleanValue() {
        return lastValueBool;
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