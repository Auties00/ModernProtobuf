package it.auties.protobuf.tool.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufName;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufReserved;
import it.auties.protobuf.base.ProtobufType;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

@UtilityClass
public class AstElements {
    public final String PROTOBUF_MESSAGE = ProtobufMessage.class.getName();
    public final String PROTOBUF_MESSAGE_NAME = ProtobufName.class.getName();
    public final String PROTOBUF_PROPERTY = ProtobufProperty.class.getName();
    public final String PROTOBUF_RESERVED = ProtobufReserved.class.getName();
    public final String PROTOBUF_TYPE = ProtobufType.class.getName();
    public final String JSON_CREATOR = JsonCreator.class.getName();
    public final String ARRAYS = Arrays.class.getName();
    public final String STREAM = Stream.class.getName();
    public final String OBJECT = Object.class.getName();
    public final String PREDICATE = Predicate.class.getName();
    public final String DEPRECATED = Deprecated.class.getName();
    public final String NON_NULL = findLombokAnnotation("NonNull");
    public final String BUILDER = findLombokAnnotation("Builder");
    public final String ALL_ARGS_CONSTRUCTOR = findLombokAnnotation("AllArgsConstructor");
    public final String GETTER = findLombokAnnotation("Getter");
    public final String DATA = findLombokAnnotation("Data");
    public final String DEFAULT = findLombokAnnotation("Builder.Default");
    public final String JACKSONIZED = findLombokAnnotation("extern.jackson.Jacksonized");
    public static final String OPTIONAL = Optional.class.getName();
    public static final Map<String, String> IMPORTS = new HashMap<>();

    static {
        IMPORTS.put("ProtobufMessage", PROTOBUF_MESSAGE);
        IMPORTS.put("ProtobufName", PROTOBUF_MESSAGE_NAME);
        IMPORTS.put("ProtobufProperty", PROTOBUF_PROPERTY);
        IMPORTS.put("ProtobufReserved", PROTOBUF_RESERVED);
        IMPORTS.put("JsonCreator", JSON_CREATOR);
        IMPORTS.put("Arrays", ARRAYS);
        IMPORTS.put("Deprecated", DEPRECATED);
        IMPORTS.put("NonNull", NON_NULL);
        IMPORTS.put("Builder", BUILDER);
        IMPORTS.put("AllArgsConstructor", ALL_ARGS_CONSTRUCTOR);
        IMPORTS.put("Getter", GETTER);
        IMPORTS.put("DATA", DATA);
        IMPORTS.put("Default", DEFAULT);
        IMPORTS.put("Jacksonized", JACKSONIZED);
        IMPORTS.put("ProtobufType", PROTOBUF_TYPE);
    }

    private String findLombokAnnotation(String annotation){
        return "lombok.%s".formatted(annotation);
    }
}
