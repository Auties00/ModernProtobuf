package it.auties.protobuf.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.util.Objects;

@UtilityClass
public class ProtobufUtils {
    public int parseIndex(Field field) {
        var annotation = field.getAnnotation(JsonProperty.class);
        Objects.requireNonNull(annotation, "Cannot parse index: missing property annotation");
        return Integer.parseUnsignedInt(annotation.value());
    }

    public String parseType(Field field) {
        var annotation = field.getAnnotation(JsonPropertyDescription.class);
        return annotation != null ? annotation.value()
                : "unknown";
    }

    public boolean isRequired(Field field) {
        var annotation = field.getAnnotation(JsonProperty.class);
        Objects.requireNonNull(annotation, "Cannot parse index: missing property annotation");
        return annotation.required();
    }
}
