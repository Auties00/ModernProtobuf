package it.auties.protobuf.jackson;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufProperty;
import it.auties.protobuf.model.ProtobufSchema;
import it.auties.protobuf.util.ArrayInputStream;
import it.auties.protobuf.util.ProtobufField;
import it.auties.protobuf.util.ProtobufUtils;
import it.auties.protobuf.util.VersionInfo;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProtobufParser extends ParserMinimalBase {
    private static final Map<Class<? extends ProtobufMessage>, Map<Integer, ProtobufField>> fieldsCache = new ConcurrentHashMap<>();

    private final LinkedList<ArrayInputStream> inputs;
    private final LinkedList<Class<? extends ProtobufMessage>> types;
    private final IOContext ioContext;
    private ObjectCodec codec;
    private boolean closed;
    private int lastIndex;
    private int lastType;
    private Object lastValue;

    public ProtobufParser(IOContext ioContext, int parserFeatures, ObjectCodec codec, byte[] input) {
        super(parserFeatures);
        this.inputs = new LinkedList<>();
        addInput(input);
        this.types = new LinkedList<>();
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
    public boolean isExpectedStartObjectToken() {
        return true;
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
        if(schema == null){
            return;
        }

        var type = ((ProtobufSchema) schema).messageType();
        addType(type);
    }

    public Object lastValue() {
        return lastValue;
    }

    private ArrayInputStream input(){
        return Objects.requireNonNull(inputs.peekFirst());
    }

    private Class<? extends ProtobufMessage> type(){
        return Objects.requireNonNull(types.peekFirst());
    }

    private ProtobufField field(){
        return fieldsCache.get(type())
                .get(lastIndex);
    }

    private void addInput(byte[] bytes) {
        inputs.addFirst(new ArrayInputStream(bytes));
    }

    private void addType(Class<? extends ProtobufMessage> type) {
        types.addFirst(type);
        cacheFields(type);
    }

    private void cacheFields(Class<? extends ProtobufMessage> clazz) {
        if (fieldsCache.containsKey(type())) {
            return;
        }

        var fields = createProtobufFields(clazz);
        fieldsCache.put(clazz, fields);
        if (clazz.getSuperclass() == null || !ProtobufMessage.isMessage(clazz.getSuperclass())) {
            return;
        }

        cacheFields(clazz.getSuperclass().asSubclass(ProtobufMessage.class));
    }

    private ConcurrentMap<Integer, ProtobufField> createProtobufFields(Class<? extends ProtobufMessage> type) {
        return Stream.of(type.getDeclaredFields(), type.getFields())
                .flatMap(Arrays::stream)
                .filter(ProtobufUtils::isProperty)
                .map(field -> Map.entry(field.getName(), ProtobufUtils.getProperty(field).orElseThrow()))
                .collect(Collectors.toConcurrentMap(entry -> entry.getValue().index(), this::createProtobufField));
    }

    private ProtobufField createProtobufField(Entry<String, ProtobufProperty> entry) {
        return new ProtobufField(
                entry.getKey(),
                entry.getValue().index(),
                ProtobufUtils.getProtobufType(entry.getValue()),
                ProtobufUtils.getMessageType(ProtobufUtils.getJavaType(entry.getValue())),
                null,
                entry.getValue().packed(),
                entry.getValue().required(),
                entry.getValue().repeated()
        );
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return schema instanceof ProtobufSchema;
    }

    @Override
    public JsonToken nextToken() throws IOException {
        if (type() == null) {
            throw new ProtobufDeserializationException("Cannot deserialize message: missing schema");
        }

        if (!hasCurrentToken()) {
            return super._currToken = JsonToken.START_OBJECT;
        }

        if (_currToken == JsonToken.FIELD_NAME) {
            this.lastValue = switch (lastType) {
                case 0 -> input().readInt64();
                case 1 -> input().readFixed64();
                case 2 -> readDelimited();
                case 3 -> addMessage(input().readBytes(), field().messageType());
                case 4 -> null;
                case 5 -> input().readFixed32();
                default -> throw new ProtobufDeserializationException("Protocol message(%s) had invalid wire type(%s)".formatted(lastIndex, lastType));
            };

            return super._currToken = switch (lastType) {
                case 0, 1, 5 -> JsonToken.VALUE_NUMBER_INT;
                case 2, 3 -> JsonToken.VALUE_EMBEDDED_OBJECT;
                case 4 -> endObject();
                default -> throw new ProtobufDeserializationException("Field with index %s inside message %s has an invalid wire type: %s"
                        .formatted(lastIndex, type().getName(), lastType));
            };
        }

        var tag = input().readTag();
        if (tag == 0) {
            return endObject();
        }

        this.lastIndex = tag >>> 3;
        if (lastIndex == 0) {
            throw ProtobufDeserializationException.invalidTag();
        }

        this.lastType = tag & 7;
        return super._currToken = JsonToken.FIELD_NAME;
    }

    private JsonToken endObject() {
        inputs.removeFirst();
        types.removeFirst();
        return JsonToken.END_OBJECT;
    }

    private Object readDelimited() throws IOException {
        var read = input().readBytes();
        var field = field();
        if (!field.packed()) {
            return switch (field.type()) {
                case BYTES -> read;
                case STRING -> new String(read, StandardCharsets.UTF_8);
                case MESSAGE -> addMessage(read, field.messageType());
                default -> throw new IllegalStateException("Unexpected value: " + field.type());
            };
        }

        var int64 = field.type().isLong();
        if (field.type().isInt() || int64) {
            return readPacked(read, int64);
        }

        throw new ProtobufDeserializationException("Field with index %s at %s is marked as packed but isn't an int or a long"
                .formatted(field.index(), type().getName()));
    }

    private Object addMessage(byte[] read, Class<? extends ProtobufMessage> type) {
        addInput(read);
        addType(type);
        return null;
    }

    private List<? extends Number> readPacked(byte[] read, boolean int64) throws IOException {
        var stream = new ArrayInputStream(read);
        var results = new ArrayList<Number>();
        while (!stream.isAtEnd()) {
            results.add(int64 ? stream.readRawVarint64() : stream.readRawVarint32());
        }

        return results;
    }

    @Override
    public String getCurrentName() {
        return Objects.requireNonNull(field()).name();
    }

    @Override
    public JsonLocation getTokenLocation() {
        return new JsonLocation(ioContext.contentReference(),
                input().position(),
                -1, -1, input().position());
    }

    @Override
    public void overrideCurrentName(String name) {
        throw new UnsupportedOperationException("Cannot override current name with %s".formatted(name));
    }

    @Override
    public String getText() {
        return (String) lastValue();
    }

    @Override
    public char[] getTextCharacters() {
        return getText() != null ? getText().toCharArray() : null;
    }

    @Override
    public boolean hasTextCharacters() {
        return false;
    }

    @Override
    public int getTextLength() {
        return getText() != null ? getText().length() : 0;
    }

    @Override
    public int getTextOffset() {
        return 0;
    }

    @Override
    public Number getNumberValue() {
        return (Number) lastValue();
    }

    @Override
    public NumberType getNumberType() {
        var type = field().type();
        return switch (type){
            case FLOAT -> NumberType.FLOAT;
            case DOUBLE -> NumberType.DOUBLE;
            case INT32, SINT32, UINT32, FIXED32, SFIXED32, BOOLEAN -> NumberType.INT;
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> NumberType.LONG;
            default -> throw new IllegalStateException("Unexpected value: %s".formatted(type));
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
    public BigInteger getBigIntegerValue() {
        throw new UnsupportedOperationException("Big integers are not supported");
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
    public BigDecimal getDecimalValue() {
        throw new UnsupportedOperationException("Big integers are not supported");
    }

    @Override
    public Object getEmbeddedObject() {
        return lastValue();
    }

    @Override
    public byte[] getBinaryValue(Base64Variant decoder) {
        return switch (lastValue()){
            case String string -> decoder.decode(string);
            case byte[] bytes -> bytes;
            default -> throw new IllegalStateException("Unexpected value");
        };
    }
}