package it.auties.protobuf.tool.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufMessageName;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.parser.type.ProtobufMessageType;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

@UtilityClass
public class AstElements {
    public final String PROTOBUF_MESSAGE = ProtobufMessage.class.getName();
    public final String PROTOBUF_MESSAGE_TYPE = ProtobufMessageType.class.getName();
    public final String PROTOBUF_MESSAGE_NAME = ProtobufMessageName.class.getName();
    public final String PROTOBUF_PROPERTY = ProtobufProperty.class.getName();
    public final String JSON_CREATOR = JsonCreator.class.getName();
    public final String ARRAYS = Arrays.class.getName();
    public final String STREAM = Stream.class.getName();
    public final String OBJECT = Object.class.getName();
    public final String PREDICATE = Predicate.class.getName();
    public final String DEPRECATED = Deprecated.class.getName();
    public final String NON_NULL = findLombokAnnotation("NonNull");
    public final String BUILDER = findLombokAnnotation("Builder");
    public final String DEFAULT = findLombokAnnotation("Builder.Default");
    public final String JACKSONIZED = findLombokAnnotation("extern.jackson.Jacksonized");
    public final String ALL_ARGS_CONSTRUCTOR = findLombokAnnotation("AllArgsConstructor");
    public static final String LIST = List.class.getName();
    public static final String ARRAY_LIST = ArrayList.class.getName();
    public static final String COLLECTION = Collection.class.getName();
    public static final String OVERRIDE = Override.class.getName();
    public static final String OPTIONAL = Optional.class.getName();

    private static String findLombokAnnotation(String annotation){
        return "lombok.%s".formatted(annotation);
    }
}
