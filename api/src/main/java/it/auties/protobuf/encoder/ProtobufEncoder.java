package it.auties.protobuf.encoder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.experimental.ExtensionMethod;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.util.Arrays;

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
        Arrays.stream(object.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(JsonProperty.class) && field.isAnnotationPresent(JsonPropertyDescription.class))
                .map(IllegalReflection::opened)
                .map(field -> createFieldOrThrow(object, field))
                .filter(ProtobufField::valid)
                .forEach(field -> encodeField(output, field));

        return output.readResult();
    }

    private ProtobufField createFieldOrThrow(Object object, Field field) {
        try {
            return new ProtobufField(field.getAnnotation(JsonProperty.class), field.getAnnotation(JsonPropertyDescription.class), field.get(object));
        }catch (IllegalAccessException exception){
            throw new IllegalStateException("Access failed: reflection opener failed", exception);
        }
    }

    private void encodeField(ArrayOutputStream output, ProtobufField field) {
        var number = Integer.parseInt(field.property().value());
        var type = field.description()
                .value()
                .replace("[packed]", "")
                .trim();
        switch (type){
            case "float", "fixed32", "sfixed32" -> output.writeFixed32(number, Float.floatToRawIntBits((float) field.value()));
            case "double", "fixed64", "sfixed64" -> output.writeFixed64(number, Double.doubleToRawLongBits((double) field.value()));
            case "bool" -> output.writeBool(number, (boolean) field.value());
            case "string" -> output.writeString(number, (String) field.value());
            case "bytes" -> output.writeBytes(number, (byte[]) field.value());
            case "int32", "uint32", "sint32" -> output.writeUInt32(number, (int) field.value());
            case "int64", "uint64", "sint64" -> output.writeUInt64(number, (long) field.value());
            default -> encodeFieldFallback(output, field, number);
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
