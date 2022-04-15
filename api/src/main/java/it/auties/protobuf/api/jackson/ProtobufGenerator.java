package it.auties.protobuf.api.jackson;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.json.JsonWriteContext;
import it.auties.protobuf.api.exception.ProtobufException;
import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.exception.ProtobufSerializationException;
import it.auties.protobuf.api.util.ArrayOutputStream;
import it.auties.protobuf.api.util.ProtobufField;
import it.auties.protobuf.api.util.ProtobufUtils;
import it.auties.reflection.Reflection;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

// Would be better to follow the flow of GeneratorBase, but it requires to override the field discover system
@ExtensionMethod(Reflection.class)
class ProtobufGenerator extends GeneratorBase {
    protected final static JsonWriteContext BOGUS_WRITE_CONTEXT = JsonWriteContext.createRootContext(null);
    private final OutputStream output;

    public ProtobufGenerator(int jsonFeatures, ObjectCodec codec, OutputStream output) {
        super(jsonFeatures, codec, BOGUS_WRITE_CONTEXT);
        this.output = output;
    }

    @Override
    public void writeStartObject() {

    }

    @Override
    public void writeEndObject() throws IOException {
        if(getCurrentValue() == null){
            return;
        }

        if(!ProtobufMessage.isMessage(getCurrentValue().getClass())){
            throw new ProtobufSerializationException("Cannot encode protobuf message: %s is not a valid message"
                    .formatted(getCurrentValue().getClass().getName()));
        }

        var result = encode(getCurrentValue());
        output.write(result);
    }

    public byte[] encode(Object object) {
        if(object == null){
            return new byte[0];
        }

        try {
            var output = new ArrayOutputStream();
            return encodeObject(object, output);
        }catch (ProtobufException exception){
            throw exception;
        }catch (Throwable throwable){
            throw new ProtobufSerializationException("An unknown exception occured while serializing", throwable);
        }
    }

    private byte[] encodeObject(Object object, ArrayOutputStream output) {
        Stream.of(object.getClass().getFields(), object.getClass().getDeclaredFields())
                .flatMap(Arrays::stream)
                .map(Reflection::open)
                .filter(ProtobufUtils::isProperty)
                .map(field -> createField(object, field))
                .filter(ProtobufField::valid)
                .forEach(field -> encodeField(output, field));

        return output.buffer().toByteArray();
    }

    private ProtobufField createField(Object object, Field field) {
        var property = ProtobufUtils.getProperty(field);
        var value = getFieldValue(object, field);
        return new ProtobufField(
                field.getName(),
                property.index(),
                property.type(),
                value,
                property.packed(),
                property.required(),
                property.repeated(),
                property.requiresConversion()
        );
    }

    @SneakyThrows
    private Object getFieldValue(Object object, Field field) {
        var value = field.get(object);
        if (!ProtobufMessage.isMessage(field.getType()) || !ProtobufUtils.hasValue(field.getType())) {
            return value;
        }

        var method = value.getClass().getMethod("value");
        Reflection.open(method);
        return method.invoke(value);
    }

    private void encodeField(ArrayOutputStream output, ProtobufField field) {
        try {
            if(field.repeated()){
                encodeRepeatedFields(output, field);
                return;
            }

            switch (field.type()){
                case BOOLEAN -> output.writeBool(field.index(), field.valueAs());
                case STRING -> output.writeString(field.index(), field.valueAs());
                case BYTES -> output.writeByteArray(field.index(), field.valueAs());

                case FLOAT -> output.writeFixed32(field.index(), Float.floatToRawIntBits(field.valueAs()));
                case DOUBLE -> output.writeFixed64(field.index(), Double.doubleToRawLongBits(field.valueAs()));

                case INT32, SINT32 -> output.writeInt32(field.index(), field.valueAs());
                case UINT32 -> output.writeUInt32(field.index(), field.valueAs());
                case FIXED32, SFIXED32 -> output.writeFixed32(field.index(), field.valueAs());

                case INT64, SINT64 -> output.writeInt64(field.index(), field.valueAs());
                case UINT64  -> output.writeUInt64(field.index(), field.valueAs());
                case FIXED64, SFIXED64 -> output.writeFixed64(field.index(), field.valueAs());

                default -> encodeFieldFallback(field.index(), field.value(), output);
            }
        }catch (ClassCastException exception){
            throw new RuntimeException("A field misreported its own type in a schema: %s".formatted(field), exception);
        }
    }

    private void encodeRepeatedFields(ArrayOutputStream output, ProtobufField field) {
        field.<Collection<?>>valueAs()
                .stream()
                .map(field::withValue)
                .forEach(entry -> encodeField(output, entry));
    }

    private void encodeFieldFallback(int index, Object value, ArrayOutputStream output) {
        if (value instanceof Enum<?>) {
            output.writeUInt64(index, findEnumIndex(value));
            return;
        }

        output.writeByteArray(index, encode(value));
    }

    private int findEnumIndex(Object object){
        try {
            return (int) object.getClass()
                    .getMethod("index")
                    .open()
                    .invoke(object);
        }catch (Throwable throwable){
            return findEnumIndexFallback(object);
        }
    }

    private int findEnumIndexFallback(Object object) {
        try {
            return (int) object.getClass()
                    .getMethod("ordinal")
                    .open()
                    .invoke(object);
        }catch (Throwable throwable){
            throw new RuntimeException("An exception occurred while invoking the index method for the enum", throwable);
        }
    }

    @Override
    public void flush() throws IOException {
        output.flush();
    }

    @Override
    protected void _releaseBuffers() {
        try {
            output.close();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot close output buffer", exception);
        }
    }

    @Override
    protected void _verifyValueWrite(String typeMsg) {

    }

    @Override
    public void writeStartArray() {
        
    }

    @Override
    public void writeEndArray() {
        
    }
    
    @Override
    public void writeFieldName(String name) {
        throw new ProtobufSerializationException("Detected mixed json and protobuf annotations. " +
                "Please remove all json annotations to avoid conflicts");
    }

    @Override
    public void writeString(String text) {
        
    }

    @Override
    public void writeString(char[] buffer, int offset, int len) {
        
    }

    @Override
    public void writeRawUTF8String(byte[] buffer, int offset, int len) {
        
    }

    @Override
    public void writeUTF8String(byte[] buffer, int offset, int len) {
        
    }

    @Override
    public void writeRaw(String text) {
        
    }

    @Override
    public void writeRaw(String text, int offset, int len) {
        
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) {
        
    }

    @Override
    public void writeRaw(char c) {
        
    }

    @Override
    public void writeBinary(Base64Variant bv, byte[] data, int offset, int len) {
        
    }

    @Override
    public void writeNumber(int v) {
        
    }

    @Override
    public void writeNumber(long v) {
        
    }

    @Override
    public void writeNumber(BigInteger v) {
        
    }

    @Override
    public void writeNumber(double v) {
        
    }

    @Override
    public void writeNumber(float v) {
        
    }

    @Override
    public void writeNumber(BigDecimal v) {
        
    }

    @Override
    public void writeNumber(String encodedValue) {
        
    }

    @Override
    public void writeBoolean(boolean state) {
        
    }

    @Override
    public void writeNull() {
        
    }
}
