package it.auties.protobuf.decoder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.java.Log;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElse;

@RequiredArgsConstructor(staticName = "forType")
@Accessors(fluent = true, chain = true)
@Log
public class ProtobufDecoder<T> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new Jdk8Module());

    @NonNull
    private final Class<? extends T> modelClass;

    @Setter
    private boolean warnUnknownFields;

    private final LinkedList<Class<?>> classes = new LinkedList<>();

    public T decode(byte[] input) throws IOException {
        var map = decodeAsMap(input);
        return OBJECT_MAPPER.convertValue(map, modelClass);
    }

    public Map<Integer, Object> decodeAsMap(byte[] input) throws IOException {
        return decode(new ArrayInputStream(input));
    }

    public String decodeAsJson(byte[] input) throws IOException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(decodeAsMap(input));
    }

    private Map<Integer, Object> decode(ArrayInputStream input) throws IOException {
        var results = new ArrayList<Map.Entry<Integer, Object>>();
        while (true) {
            var tag = input.readTag();
            if (tag == 0) {
                break;
            }

            var current = parseField(input, tag);
            if (current.isEmpty()) {
                break;
            }

            results.add(current.get());
        }

        input.checkLastTagWas(0);
        return results.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, this::handleDuplicatedFields));
    }

    private <F, S> List<?> handleDuplicatedFields(F first, S second) {
        return Stream.of(first, second)
                .map(entry -> entry instanceof Collection<?> collection ? collection : List.of(entry))
                .flatMap(Collection::stream)
                .toList();
    }

    private Optional<Map.Entry<Integer, Object>> parseField(ArrayInputStream input, int tag) throws IOException {
        var number = tag >>> 3;
        if (number == 0) {
            throw InvalidProtocolBufferException.invalidTag();
        }

        var content = readFieldContent(input, tag, number);
        return Optional.ofNullable(content)
                .map(parsed -> Map.entry(number, parsed));
    }

    private Object readFieldContent(ArrayInputStream input, int tag, int number) throws IOException {
        var type = tag & 7;
        return switch (type) {
            case 0 -> input.readInt64();
            case 1 -> input.readFixed64();
            case 2 -> readDelimited(input, number);
            case 3 -> readGroup(input);
            case 4 -> endGroup();
            case 5 -> input.readFixed32();
            default -> throw new InvalidProtocolBufferException.InvalidWireTypeException("Protocol message(%s) had invalid wire type(%s)".formatted(number, type));
        };
    }

    private Object endGroup() {
        classes.poll();
        return null;
    }

    private Object readGroup(ArrayInputStream input) throws IOException {
        var read = input.readBytes();
        var stream = new ArrayInputStream(read);
        return decode(stream);
    }

    private Object readDelimited(ArrayInputStream input, int fieldNumber) throws IOException {
        var read = input.readBytes();
        var type = findPropertyType(fieldNumber);
        return convertBytesToType(read, type.orElse(byte[].class));
    }

    private Object convertBytesToType(byte[] read, Class<?> type) throws IOException{
        if(byte[].class.isAssignableFrom(type)){
            return read;
        }

        if (String.class.isAssignableFrom(type)) {
            return new String(read, StandardCharsets.UTF_8);
        }

        if(Number.class.isAssignableFrom(type)){
            return readBytesAsNumber(read, type);
        }

        return readDelimited(type, read);
    }

    private Number readBytesAsNumber(byte[] read, Class<?> type) throws IOException {
        var stream = new ArrayInputStream(read);
        var primitiveType = read[0] & 7;
        return switch (primitiveType) {
            case 0 -> stream.readInt64();
            case 1 -> stream.readFixed64();
            case 5 -> stream.readFixed32();
            default -> throw new InvalidProtocolBufferException.InvalidWireTypeException("Protocol message had invalid wire type(%s)".formatted(type));
        };
    }


    private Object readDelimited(Class<?> currentClass, byte[] read){
        try {
            classes.push(currentClass);
            var stream = new ArrayInputStream(read);
            return decode(stream);
        } catch (IOException ex) {
            return new String(read, StandardCharsets.UTF_8);
        }finally {
            classes.poll();
        }
    }

    private Optional<Class<?>> findPropertyType(int fieldNumber) {
        var result = findFields()
                .stream()
                .filter(field -> isProperty(field, fieldNumber))
                .<Class<?>>map(this::foldType)
                .findAny();

        if(result.isEmpty() && warnUnknownFields){
            log.info("Detected unknown field at index %s inside class %s"
                    .formatted(fieldNumber, requireNonNullElse(classes.peekFirst(), modelClass).getName()));
        }

        return result;
    }

    private Class<?> foldType(Field field) {
        if(!Collection.class.isAssignableFrom(field.getType())){
            return field.getType();
        }

        var parameterizedType = (ParameterizedType) field.getGenericType();
        return (Class<?>) parameterizedType.getActualTypeArguments()[0];
    }

    private List<Field> findFields(){
        return Optional.ofNullable(classes.peekFirst())
                .map(this::findFields)
                .orElse(Arrays.asList(modelClass.getDeclaredFields()));
    }

    private List<Field> findFields(Class<?> clazz){
        if(clazz == null){
            return List.of();
        }

        var fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));
        fields.addAll(findFields(clazz.getSuperclass()));
        return fields;
    }

    private boolean isProperty(Field field, int fieldNumber) {
        return Optional.ofNullable(field.getAnnotation(JsonProperty.class))
                .map(JsonProperty::value)
                .filter(entry -> Objects.equals(entry, String.valueOf(fieldNumber)))
                .isPresent();
    }
}
