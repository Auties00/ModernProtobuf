package it.auties.protobuf.serialization.performance;

import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.serialization.exception.ProtobufDeserializationException;
import it.auties.protobuf.serialization.stream.ArrayInputStream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.java.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@RequiredArgsConstructor(staticName = "of")
@Accessors(fluent = true, chain = true)
@Log
public class ProtobufDecoder<T> {
    private static final ConcurrentMap<Class<?>, HashMap<Integer, ProtobufProperty>> fieldsCache = new ConcurrentHashMap<>();

    @NonNull
    private final Class<? extends T> modelClass;

    @NonNull
    public final Supplier<?> builder;

    @NonNull
    public final Map<Integer, BiConsumer> setters;

    private final LinkedList<Class<?>> classes = new LinkedList<>();

    public Object decode(byte[] input) throws IOException {
        return decode(new ArrayInputStream(input));
    }

    @SneakyThrows
    private Object decode(ArrayInputStream input) throws IOException {
        var instance = builder.get();
        while (true) {
            var tag = input.readTag();
            if (tag == 0) {
                break;
            }

            var number = tag >>> 3;
            if (number == 0) {
                throw ProtobufDeserializationException.invalidTag(tag);
            }

            setters.get(number).accept(instance, readFieldContent(input, tag, number));
        }

        return instance;
    }

    private Object readFieldContent(ArrayInputStream input, int tag, int number) throws IOException {
        var protobufProperty = getFields().get(number);
        return switch (tag & 7) {
            case 0 -> {
                var value = input.readInt64();
                yield switch (protobufProperty.type()) {
                    case INT32, SINT32, UINT32, MESSAGE -> (int) value;
                    case INT64, SINT64, UINT64 -> value;
                    case BOOL -> value == 1;
                    default -> throw new IllegalStateException("Unexpected value: " + protobufProperty.type());
                };
            }
            case 1 -> {
                var value = input.readFixed64();
                if (protobufProperty.type() == ProtobufType.DOUBLE) {
                    yield Double.longBitsToDouble(value);
                }

                yield value;
            }
            case 2 -> {
                var read = input.readBytes();
                if(protobufProperty == null){
                    yield read;
                }

                if(protobufProperty.packed()){
                    var stream = new ArrayInputStream(read);
                    yield stream.readInt64();
                }

                yield switch (protobufProperty.type()) {
                    case BYTES -> read;
                    case STRING -> new String(read, StandardCharsets.UTF_8);
                    default -> {
                        try {
                            classes.push(protobufProperty.implementation());
                            var stream = new ArrayInputStream(read);
                            yield decode(stream);
                        } catch (IOException ex) {
                            yield new String(read, StandardCharsets.UTF_8);
                        }finally {
                            classes.poll();
                        }
                    }
                };
            }
            case 3 -> {
                var read = input.readBytes();
                var stream = new ArrayInputStream(read);
                yield decode(stream);
            }
            case 4 -> {
                classes.poll();
                yield null;
            }
            case 5 -> {
                var value = input.readFixed32();
                if (protobufProperty.type() == ProtobufType.FLOAT) {
                    yield Float.intBitsToFloat(value);
                }

                yield value;
            }
            default -> throw new ProtobufDeserializationException("Protocol message had invalid wire type");
        };
    }

    private Map<Integer, ProtobufProperty> getFields(){
        return Optional.ofNullable(classes.peekFirst())
                .map(ProtobufDecoder::getField)
                .orElseGet(() -> getField(modelClass));
    }

    private static HashMap<Integer, ProtobufProperty> getField(Class<?> clazz){
        if(clazz == null){
            return new HashMap<>();
        }

        if(fieldsCache.containsKey(clazz)){
            return fieldsCache.get(clazz);
        }

        var fields = getProtobufFields(clazz);
        fields.putAll(getField(clazz.getSuperclass()));
        fieldsCache.put(clazz, fields);
        return fields;
    }

    private static HashMap<Integer, ProtobufProperty> getProtobufFields(Class<?> clazz) {
        var map = new HashMap<Integer, ProtobufProperty>();
        for (var fields : Arrays.asList(clazz.getFields(), clazz.getDeclaredFields())) {
            for (var entry : fields) {
                var annotation = entry.getAnnotation(it.auties.protobuf.base.ProtobufProperty.class);
                if(annotation == null){
                    continue;
                }
                var protobufProperty = new ProtobufProperty(annotation.index(), annotation.type(), annotation.implementation(), annotation.name(), annotation.required(), annotation.ignore(), annotation.packed(), annotation.repeated());
                map.putIfAbsent(protobufProperty.index(), protobufProperty);
            }
        }
        return map;
    }

    private record ProtobufProperty(int index, ProtobufType type, Class<?> implementation, String name, boolean required, boolean ignore, boolean packed, boolean repeated) {

    }
}