package it.auties.protobuf.decoder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import it.auties.protobuf.json.ByteStringModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor(staticName = "forType")
public class ProtobufDecoder<T> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new Jdk8Module())
            .registerModule(new ByteStringModule());

    private final Class<? extends T> modelClass;
    private LinkedList<Class<?>> classes = new LinkedList<>();

    public T decode(byte[] input) throws IOException {
        return OBJECT_MAPPER.convertValue(decode(new ArrayInputStream(input)), modelClass);
    }

    public Map<Integer, Object> decodeAsMap(byte[] input) throws IOException {
        return decode(new ArrayInputStream(input));
    }

    public String decodeAsJson(byte[] input) throws IOException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(decodeAsMap(input));
    }

    private Map<Integer, Object> decode(ArrayInputStream input) throws IOException {
        var results = new ArrayList<Map.Entry<Integer, Object>>();
        while (true) {
            var tag = input.readTag();
            if (tag == 0) {
                break;
            }

            var current = parseUnknownField(input, tag);
            if (current.isEmpty()) {
                break;
            }

            results.add(current.get());
        }

        input.checkLastTagWas(0);
        return results.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, this::handleDuplicatedFields));
    }

    private <F, S> List<?> handleDuplicatedFields(F first, S second) {
        return Stream.of(first, second)
                .map(entry -> entry instanceof Collection<?> collection ? collection : List.of(entry))
                .flatMap(Collection::stream)
                .toList();
    }

    private Optional<Map.Entry<Integer, Object>> parseUnknownField(ArrayInputStream input, int tag) throws IOException {
        var number = tag >>> 3;
        if (number == 0) {
            throw InvalidProtocolBufferException.invalidTag();
        }

        var type = tag & 7;
        var content = switch (type) {
            case 0 -> input.readInt64();
            case 1 -> input.readFixed64();
            case 2 -> readGroupOrString(input, number);
            case 3 -> readGroup(input);
            case 4 -> endGroup();
            case 5 -> input.readFixed32();
            default -> throw new InvalidProtocolBufferException.InvalidWireTypeException("Protocol message(%s) had invalid wire type(%s)".formatted(number, type));
        };

        return Optional.ofNullable(content).map(parsed -> Map.entry(number, parsed));
    }

    private Object endGroup() {
        classes.poll();
        return null;
    }

    public Object readGroup(ArrayInputStream input) throws IOException {
        var read = input.readBytes();
        return decode(new ArrayInputStream(read));
    }

    private Object readGroupOrString(ArrayInputStream input, int fieldNumber) throws IOException {
        var read = input.readBytes();
        return findPropertyType(fieldNumber)
                .map(type -> isBuiltInType(type) ? new String(read) : readGroupOrString(type, read))
                .orElseGet(() -> readGroupOrString(null, read));
    }

    private Object readGroupOrString(Class<?> currentClass, byte[] read){
        try {
            classes.push(currentClass);
            return decode(new ArrayInputStream(read));
        } catch (IOException ex) {
            return new String(read);
        }finally {
            classes.poll();
        }
    }

    private Optional<Class<?>> findPropertyType(int fieldNumber) {
        return Arrays.stream(findFields())
                .filter(field -> isProperty(field, fieldNumber))
                .findAny()
                .map(Field::getType);
    }

    private Field[] findFields(){
        var clazz = classes.peekFirst();
        return clazz != null ? clazz.getDeclaredFields() : modelClass.getDeclaredFields();
    }

    private boolean isProperty(Field field, int fieldNumber) {
        return Optional.ofNullable(field.getAnnotation(JsonProperty.class))
                .map(JsonProperty::value)
                .filter(entry -> Objects.equals(entry, String.valueOf(fieldNumber)))
                .isPresent();
    }

    private boolean isBuiltInType(Class<?> clazz){
        return String.class.isAssignableFrom(clazz) || clazz.isPrimitive() || clazz.isArray();
    }
}
