package it.auties.protobuf.serializer.jackson;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.base.GeneratorBase;
import it.auties.protobuf.serializer.exception.ProtobufException;
import it.auties.protobuf.serializer.exception.ProtobufSerializationException;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.serializer.util.ArrayOutputStream;
import it.auties.protobuf.serializer.util.ProtobufField;
import it.auties.protobuf.serializer.util.ProtobufUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


class ProtobufGenerator extends GeneratorBase {
    private final OutputStream output;

    public ProtobufGenerator(int jsonFeatures, ObjectCodec codec, OutputStream output) {
        super(jsonFeatures, codec, null);
        this.output = output;
    }

    @Override
    public void writeStartObject() {

    }


    @Override
    public void writeEndObject() {

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

    @Override
    public void writeStartObject(Object forValue) throws IOException {
        if (forValue == null) {
            return;
        }

        if (!ProtobufMessage.isMessage(forValue.getClass())) {
            throw new ProtobufSerializationException("Cannot encode protobuf message: %s is not a valid message"
                    .formatted(forValue.getClass().getName()));
        }

        var result = encode(forValue);
        output.write(result);
    }

    public byte[] encode(Object object) {
        if (object == null) {
            return new byte[0];
        }

        try {
            var output = new ArrayOutputStream();
            findFields(object.getClass())
                    .stream()
                    .filter(ProtobufUtils::isProperty)
                    .peek(field -> field.setAccessible(true))
                    .map(field -> createField(object, field))
                    .filter(ProtobufField::isValid)
                    .forEach(field -> encodeField(output, field));
            return output.buffer().toByteArray();
        } catch (ProtobufException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new ProtobufSerializationException("An unknown exception occurred while serializing", throwable);
        }
    }

    private List<Field> findFields(Class<?> clazz) {
        var fields = new ArrayList<Field>();
        fields.addAll(List.of(clazz.getDeclaredFields()));
        fields.addAll(List.of(clazz.getFields()));
        if (clazz.getSuperclass() != null) {
            fields.addAll(findFields(clazz.getSuperclass()));
        }

        return fields;
    }

    private ProtobufField createField(Object object, Field field) {
        var property = ProtobufUtils.getProperty(field);
        var name = ProtobufUtils.getFieldName(field);
        var value = getFieldValue(object, field);
        return new ProtobufField(name, value, property);
    }

    private Object getFieldValue(Object object, Field field) {
        try {
            var value = field.get(object);
            return value instanceof ProtobufMessage message && message.isValueBased()
                    ? message.toValue() : value;
        } catch (ReflectiveOperationException exception) {
            throw new ProtobufSerializationException("Cannot access field %s inside class %s"
                    .formatted(field.getName(), field.getDeclaringClass().getName()), exception);
        }
    }

    private void encodeField(ArrayOutputStream output, ProtobufField field) {
        try {
            if (field.repeated()) {
                encodeRepeatedFields(output, field);
                return;
            }

            switch (field.type()) {
                case BOOL -> output.writeBool(field.index(), field.dynamicValue());
                case STRING -> output.writeString(field.index(), field.dynamicValue());
                case BYTES -> output.writeByteArray(field.index(), field.dynamicValue());

                case FLOAT -> output.writeFixed32(field.index(), Float.floatToRawIntBits(field.dynamicValue()));
                case DOUBLE -> output.writeFixed64(field.index(), Double.doubleToRawLongBits(field.dynamicValue()));

                case INT32, SINT32 -> output.writeInt32(field.index(), field.dynamicValue());
                case UINT32 -> output.writeUInt32(field.index(), field.dynamicValue());
                case FIXED32, SFIXED32 -> output.writeFixed32(field.index(), field.dynamicValue());

                case INT64, SINT64 -> output.writeInt64(field.index(), field.dynamicValue());
                case UINT64 -> output.writeUInt64(field.index(), field.dynamicValue());
                case FIXED64, SFIXED64 -> output.writeFixed64(field.index(), field.dynamicValue());

                default -> encodeFieldFallback(field.index(), field.value(), output);
            }
        } catch (ClassCastException exception) {
            throw new RuntimeException("A field misreported its own type in a schema: %s".formatted(field), exception);
        }
    }

    private void encodeRepeatedFields(ArrayOutputStream output, ProtobufField field) {
        field.<Collection<?>>dynamicValue()
                .stream()
                .map(field::toSingleField)
                .forEach(entry -> encodeField(output, entry));
    }

    private void encodeFieldFallback(int index, Object value, ArrayOutputStream output) {
        if (value instanceof Enum<?>) {
            output.writeUInt64(index, findEnumIndex(value));
            return;
        }

        output.writeByteArray(index, encode(value));
    }

    private int findEnumIndex(Object object) {
        try {
            var method = object.getClass().getMethod("index");
            method.setAccessible(true);
            return (int) method.invoke(object);
        } catch (Throwable throwable) {
            return findEnumIndexFallback(object);
        }
    }

    private int findEnumIndexFallback(Object object) {
        try {
            var method = object.getClass().getMethod("ordinal");
            method.setAccessible(true);
            return (int) method.invoke(object);
        } catch (Throwable throwable) {
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
}
