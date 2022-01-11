package it.auties.protobuf.decoder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.java.Log;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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

    private static final Map<Class<?>, List<Field>> cachedFields = new HashMap<>();

    private static final Map<Type, Class<?>> cachedTypes = new HashMap<>();

    @NonNull
    private final Class<? extends T> modelClass;

    @Setter
    private boolean warnUnknownFields;

    private final LinkedList<Class<?>> classes = new LinkedList<>();

    public T decode(byte[] input) throws IOException {
        var map = decodeAsMap(input);
        try {
            return OBJECT_MAPPER.convertValue(map, modelClass);
        }catch (Throwable throwable){
            log.warning("Map value -> %s".formatted(map));
            throw new IOException("An exception occurred while decoding a message", throwable);
        }
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
        var type = findPropertyType(fieldNumber)
                .orElse(ProtobufValue.BYTES);
        return convertValueToObject(read, type);
    }

    private Object convertValueToObject(byte[] read, ProtobufValue value) throws IOException{
        if(value.packed()){
            var stream = new ArrayInputStream(read);
            return stream.readInt64();
        }

        if(byte[].class.isAssignableFrom(value.type())){
            return read;
        }

        if (String.class.isAssignableFrom(value.type())) {
            return new String(read, StandardCharsets.UTF_8);
        }

        return readDelimited(value.type(), read);
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

    private Optional<ProtobufValue> findPropertyType(int fieldNumber) {
        var result = findFields()
                .stream()
                .filter(field -> isProperty(field, fieldNumber))
                .map(field -> new ProtobufValue(foldType(field), isPacked(field)))
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

        var genericType = field.getGenericType();
        if(genericType instanceof ParameterizedType parameterizedType){
            return (Class<?>) parameterizedType.getActualTypeArguments()[0];
        }

        var superClass = field.getType().getGenericSuperclass();
        return foldSuperClass(superClass);
    }

    private Class<?> foldSuperClass(Type superClass) {
        Objects.requireNonNull(superClass,
                "Serialization issue: cannot deduce generic type of field through class hierarchy");
        if (superClass instanceof ParameterizedType parameterizedType) {
            return (Class<?>) parameterizedType.getActualTypeArguments()[0];
        }

        var concreteSuperClass = (Class<?>) superClass;
        return foldSuperClass(concreteSuperClass.getGenericSuperclass());
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

        if(cachedFields.containsKey(clazz)){
            return cachedFields.get(clazz);
        }

        var fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));
        fields.addAll(findFields(clazz.getSuperclass()));
        cachedFields.put(clazz, fields);
        return fields;
    }

    private boolean isProperty(Field field, int fieldNumber) {
        return Optional.ofNullable(field.getAnnotation(JsonProperty.class))
                .map(JsonProperty::value)
                .filter(entry -> Objects.equals(entry, String.valueOf(fieldNumber)))
                .isPresent();
    }

    private boolean isPacked(Field field) {
        return Optional.ofNullable(field.getAnnotation(JsonPropertyDescription.class))
                .map(JsonPropertyDescription::value)
                .filter(entry -> entry.contains("[packed]"))
                .isPresent();
    }
}
