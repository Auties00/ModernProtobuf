package it.auties.protobuf.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import it.auties.protobuf.annotation.ProtobufIgnore;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.util.Objects;

@UtilityClass
public class ProtobufUtils {
    public boolean isProperty(Field field) {
        return field.isAnnotationPresent(JsonProperty.class)
                && !field.isAnnotationPresent(ProtobufIgnore.class);
    }

    public int parseIndex(Field field) {
        var annotation = field.getAnnotation(JsonProperty.class);
        Objects.requireNonNull(annotation, "Cannot parse index: missing property annotation");
        return Integer.parseUnsignedInt(annotation.value());
    }

    public String parseType(Field field, Object value) {
        var annotation = field.getAnnotation(JsonPropertyDescription.class);
        if (annotation != null) {
            return annotation.value();
        }

        return switch (value){
            case Float ignored -> "float";
            case Double ignored -> "double";
            case Boolean ignored -> "bool";
            case String ignored -> "string";
            case byte[] ignored -> "bytes";
            case Integer ignored -> "int32";
            case Long ignored -> "int64";
            case null, default -> "object";
        };
    }

    public boolean isRequired(Field field) {
        var annotation = field.getAnnotation(JsonProperty.class);
        Objects.requireNonNull(annotation, "Cannot parse index: missing property annotation");
        return annotation.required();
    }
}
