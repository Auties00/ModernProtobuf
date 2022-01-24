package it.auties.protobuf.decoder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import it.auties.protobuf.util.ProtobufUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.java.Log;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor(staticName = "forType")
@Accessors(fluent = true, chain = true)
@Log
public class ProtobufDecoder<T> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new Jdk8Module());

    private static final Map<Class<?>, List<ProtobufField>> cache = new HashMap<>();

    @NonNull
    private final Class<? extends T> modelClass;

    @Setter
    private boolean warnUnknownFields;

    private final LinkedList<Class<?>> classes = new LinkedList<>();

    @SuppressWarnings("unused")
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
            throw DeserializationException.invalidTag();
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
            default -> throw new DeserializationException("Protocol message(%s) had invalid wire type(%s)".formatted(number, type));
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
        var protobufField = getFields()
                .stream()
                .filter(field -> field.index() == fieldNumber)
                .findFirst()
                .orElseGet(() -> getFallbackType(fieldNumber));
        return convertValueToObject(read, protobufField);
    }

    private ProtobufField getFallbackType(int fieldNumber) {
        if(warnUnknownFields) {
            log.warning("Falling back to BYTES for %s in schema %s".formatted(fieldNumber, classes.peekFirst()));
        }

        return new ProtobufField(fieldNumber, byte[].class, false);
    }

    private Object convertValueToObject(byte[] read, ProtobufField value) throws IOException{
        if(value.packed()){
            return readPacked(read);
        }

        if(byte[].class.isAssignableFrom(value.type())){
            return read;
        }

        if (String.class.isAssignableFrom(value.type())) {
            return new String(read, StandardCharsets.UTF_8);
        }

        return readDelimited(value.type(), read);
    }

    private ArrayList<Integer> readPacked(byte[] read) throws IOException {
        var stream = new ArrayInputStream(read);
        var length = stream.readRawVarint32();
        var results = new ArrayList<Integer>();
        while (results.size() * 4 != length){
            var decoded = stream.readRawVarint32();
            results.add(decoded);
        }

        return results;
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

    private List<ProtobufField> getFields(){
        return Optional.ofNullable(classes.peekFirst())
                .map(this::getFields)
                .orElseGet(() -> getFields(modelClass));
    }

    private List<ProtobufField> getFields(Class<?> clazz){
        if(clazz == null){
            return List.of();
        }

        if(cache.containsKey(clazz)){
            return cache.get(clazz);
        }

        var results = new ArrayList<ProtobufField>();
        if(ProtobufTypeDescriptor.hasDescriptor(clazz)){
            var descriptor = invokeDescriptorMethod(clazz);
            descriptor.forEach((index, type) -> results.add(new ProtobufField(index, type, false)));
            results.addAll(getFields(clazz.getSuperclass()));
            cache.put(clazz, results);
            return results;
        }

        Stream.of(clazz.getFields(), clazz.getDeclaredFields())
                .flatMap(Arrays::stream)
                .filter(this::isProperty)
                .map(field -> new ProtobufField(ProtobufUtils.parseIndex(field), getPropertyType(field), isPacked(field)))
                .forEach(results::add);
        results.addAll(getFields(clazz.getSuperclass()));
        cache.put(clazz, results);
        return results;
    }

    private boolean isProperty(Field field) {
        return field.getAnnotation(JsonProperty.class) != null;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Class<?>> invokeDescriptorMethod(Class<?> clazz) {
        var descriptorMethod = getDescriptorMethod(clazz, true);
        try {
            var temp = clazz.getConstructor()
                    .newInstance();
            return (Map<Integer, Class<?>>) descriptorMethod.invoke(temp);
        }catch (Exception anotherException){
            throw new IllegalArgumentException("Cannot use descriptor to infer type inside class %s: cannot invoke descriptor method using an instance".formatted(clazz.getName()));
        }
    }

    private Method getDescriptorMethod(Class<?> clazz, boolean accessible) {
        try {
            return accessible ? clazz.getMethod("descriptor")
                    : clazz.getDeclaredMethod("descriptor");
        }catch (NoSuchMethodException exception){
            if(accessible){
                return getDescriptorMethod(clazz, false);
            }

            throw new IllegalArgumentException("Cannot use descriptor to infer type inside class %s: missing descriptor method".formatted(clazz.getName()));
        }
    }

    private Class<?> getPropertyType(Field field){
        var inferredType = inferPropertyType(field);
        var annotation = inferredType.getAnnotation(ProtobufType.class);
        return annotation != null ? annotation.value() : inferredType;
    }

    private Class<?> inferPropertyType(Field field) {
        var explicitType = field.getAnnotation(ProtobufType.class);
        if(explicitType != null){
            return explicitType.value();
        }

        if(!Collection.class.isAssignableFrom(field.getType())){
            return field.getType();
        }

        var genericType = field.getGenericType();
        if(genericType instanceof ParameterizedType parameterizedType){
            return (Class<?>) parameterizedType.getActualTypeArguments()[0];
        }

        var superClass = field.getType().getGenericSuperclass();
        return inferPropertyType(superClass);
    }

    private Class<?> inferPropertyType(Type superClass) {
        requireNonNull(superClass,
                "Serialization issue: cannot deduce generic type of field through class hierarchy");
        if (superClass instanceof ParameterizedType parameterizedType) {
            return (Class<?>) parameterizedType.getActualTypeArguments()[0];
        }

        var concreteSuperClass = (Class<?>) superClass;
        return inferPropertyType(concreteSuperClass.getGenericSuperclass());
    }

    private boolean isPacked(Field field) {
        return Optional.ofNullable(field.getAnnotation(JsonPropertyDescription.class))
                .map(JsonPropertyDescription::value)
                .filter(entry -> entry.contains("[packed]"))
                .isPresent();
    }
}
