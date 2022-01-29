package it.auties.protobuf.encoder;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.auties.protobuf.util.IllegalReflection;
import it.auties.protobuf.util.ProtobufUtils;
import lombok.experimental.ExtensionMethod;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;

@UtilityClass
@ExtensionMethod(IllegalReflection.class)
public class ProtobufEncoder {
    public byte[] encode(Object object) {
        var output = new ArrayOutputStream();
        if(object == null){
            return output.readResult();
        }

        return encodeObject(object, output);
    }

    private byte[] encodeObject(Object object, ArrayOutputStream output) {
        Stream.of(object.getClass().getFields(), object.getClass().getDeclaredFields())
                .flatMap(Arrays::stream)
                .filter(ProtobufEncoder::isProperty)
                .map(IllegalReflection::opened)
                .map(field -> createFieldOrThrow(object, field))
                .filter(ProtobufField::valid)
                .forEach(field -> encodeField(output, field));

        return output.readResult();
    }

    private boolean isProperty(Field field) {
        return field.isAnnotationPresent(JsonProperty.class);
    }

    private ProtobufField createFieldOrThrow(Object object, Field field) {
        try {
            var index = ProtobufUtils.parseIndex(field);
            var required = ProtobufUtils.isRequired(field);
            var value = field.get(object);
            var type = ProtobufUtils.parseType(field, value);
            return new ProtobufField(index, type, value, required);
        }catch (IllegalAccessException exception){
            throw new IllegalStateException("Access failed: reflection opener failed", exception);
        }
    }

    private void encodeField(ArrayOutputStream output, ProtobufField field) {
        switch (field.type()){
            case "float", "fixed32", "sfixed32" -> output.writeFixed32(field.index(), Float.floatToRawIntBits((float) field.value()));
            case "double", "fixed64", "sfixed64" -> output.writeFixed64(field.index(), Double.doubleToRawLongBits((double) field.value()));
            case "bool" -> output.writeBool(field.index(), (boolean) field.value());
            case "string" -> output.writeString(field.index(), (String) field.value());
            case "bytes" -> output.writeBytes(field.index(), (byte[]) field.value());
            case "int32", "uint32", "sint32" -> output.writeUInt32(field.index(), (int) field.value());
            case "int64", "uint64", "sint64" -> output.writeUInt64(field.index(), (long) field.value());
            default -> encodeFieldFallback(output, field, field.index());
        }
    }

    private void encodeFieldFallback(ArrayOutputStream output, ProtobufField field, int number) {
        if (field.value() instanceof Enum<?>) {
            output.writeUInt64(number, findEnumIndex(field.value()));
            return;
        }

        output.writeBytes(number, encode(field.value()));
    }

    private int findEnumIndex(Object object){
        try {
            return (int) object.getClass()
                    .getMethod("index")
                    .opened()
                    .invoke(object);
        }catch (Throwable throwable){
            return findEnumIndexFallback(object);
        }
    }

    private int findEnumIndexFallback(Object object) {
        try {
            return (int) object.getClass()
                    .getMethod("ordinal")
                    .opened()
                    .invoke(object);
        }catch (Throwable throwable){
            throw new RuntimeException("An exception occurred while invoking the index method for the enum", throwable);
        }
    }
}
