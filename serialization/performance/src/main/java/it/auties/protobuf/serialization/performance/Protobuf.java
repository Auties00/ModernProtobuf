package it.auties.protobuf.serialization.performance;

import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.serialization.exception.ProtobufDeserializationException;
import it.auties.protobuf.serialization.exception.ProtobufException;
import it.auties.protobuf.serialization.exception.ProtobufSerializationException;
import it.auties.protobuf.serialization.model.WireType;
import it.auties.protobuf.serialization.performance.model.ProtobufAccessors;
import it.auties.protobuf.serialization.performance.model.ProtobufField;
import it.auties.protobuf.serialization.performance.model.ProtobufModel;
import it.auties.protobuf.serialization.performance.processor.ProtobufAnnotation;
import it.auties.protobuf.serialization.stream.ArrayInputStream;
import it.auties.protobuf.serialization.stream.ArrayOutputStream;
import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static it.auties.protobuf.serialization.model.WireType.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class Protobuf<T> {
    private static final Map<Class<?>, ProtobufModel> propertiesMap;
    static {
        try {
            var clazz = Class.forName("it.auties.protobuf.ProtobufStubs");
            var field = clazz.getField("properties");
            field.setAccessible(true);
            propertiesMap = new HashMap<>((Map<Class<?>, ProtobufModel>) field.get(null));
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Cannot initialize decoder", exception);
        }
    }

    private final Supplier<?> builder;
    private final Function build;
    private final Map<Integer, ProtobufAccessors> accessors;
    public Protobuf(@NonNull Class<T> clazz) {
        var properties = propertiesMap.get(clazz);
        this.builder = properties.builder();
        this.build = properties.build();
        this.accessors = properties.accessors();
    }

    @SuppressWarnings("unused")
    public static <T> Protobuf<T> of(Class<T> clazz) {
        return new Protobuf<>(clazz);
    }
    
    public static <T> T readMessage(byte[] message, Class<T> clazz) throws IOException {
        var decoder = new Protobuf<>(clazz);
        return decoder.decode(message);
    }

    public static byte[] writeMessage(Object object) {
        var encoder = new Protobuf<>(object.getClass());
        return encoder.encode(object);
    }

    @SuppressWarnings("unchecked")
    public T decode(byte[] bytes) throws IOException {
        var input = new ArrayInputStream(bytes);
        var instance = builder.get();
        var repeatedFieldsMap = new HashMap<Integer, List<Object>>();
        var repeatedMatches = false;
        while (true) {
            var tag = input.readTag();
            if (tag == 0) {
                break;
            }

            var number = tag >>> 3;
            if (number == 0) {
                throw ProtobufDeserializationException.invalidTag(tag);
            }

            var accessor = accessors.get(number);
            var property = accessor != null ? accessor.record() : null;
            var value = readFieldContent(input, tag, property);
            if(property != null && !property.repeated()){
                accessor.setter().accept(instance, value);
            }else if(accessor != null){
                repeatedMatches = true;
                var repeatedWrapper = repeatedFieldsMap.computeIfAbsent(number, ignored -> new ArrayList<>());
                repeatedWrapper.add(value);
            }
        }

        if(repeatedMatches) {
            for (var entry : repeatedFieldsMap.entrySet()) {
                var setter = accessors.get(entry.getKey()).setter();
                if(setter != null){
                    setter.accept(instance, entry.getValue());
                }
            }
        }

        return (T) build.apply(instance);
    }

    private Object readFieldContent(ArrayInputStream input, int tag, ProtobufField property) throws IOException {
        return switch (tag & 7) {
            case WIRE_TYPE_VAR_INT -> {
                var value = input.readInt64();
                yield switch (property == null ? null : property.type()) {
                    case INT32, SINT32, UINT32, MESSAGE -> (int) value;
                    case INT64, SINT64, UINT64 -> value;
                    case BOOL -> value == 1;
                    case null -> throw new IllegalStateException("Unexpected value");
                    default -> throw new IllegalStateException("Unexpected value: " + property.type());
                };
            }
            case WIRE_TYPE_FIXED64 -> {
                var value = input.readFixed64();
                if (property.type() == ProtobufType.DOUBLE) {
                    yield Double.longBitsToDouble(value);
                }

                yield value;
            }
            case WIRE_TYPE_LENGTH_DELIMITED -> {
                var read = input.readBytes();
                if(property == null){
                    yield read;
                }

                if(property.packed()){
                    var stream = new ArrayInputStream(read);
                    yield switch (property.type()){
                        case FIXED32, SFIXED32 -> stream.readFixed32();
                        case INT32, SINT32, UINT32 -> stream.readInt32();
                        case FLOAT -> Float.intBitsToFloat(stream.readFixed32());
                        case FIXED64, SFIXED64 -> stream.readFixed64();
                        case INT64, SINT64, UINT64  -> stream.readInt64();
                        case DOUBLE -> Double.longBitsToDouble(stream.readFixed64());
                        default -> throw new IllegalStateException("Unexpected value: " + property.type());
                    };
                }

                yield switch (property.type()) {
                    case BYTES -> read;
                    case STRING -> new String(read, StandardCharsets.UTF_8);
                    default -> {
                        if(property.type().isMessage()) {
                            var decoder = new Protobuf(property.implementation());
                            yield decoder.decode(read);
                        }

                        yield new String(read, StandardCharsets.UTF_8);
                    }
                };
            }
            case WIRE_TYPE_EMBEDDED_MESSAGE -> decode(input.readBytes());
            case WireType.WIRE_TYPE_END_OBJECT -> null;
            case WireType.WIRE_TYPE_FIXED32 -> {
                var value = input.readFixed32();
                if (property != null && property.type() == ProtobufType.FLOAT) {
                    yield Float.intBitsToFloat(value);
                }

                yield value;
            }
            default -> throw new ProtobufDeserializationException("Protocol message had invalid wire type");
        };
    }

    public byte[] encode(Object object) {
        try {
            var output = new ArrayOutputStream();
            for(var entry : accessors.values()){
                var record = entry.record();
                Function getter = entry.getter();
                var value = getter.apply(object);
                if(record.required() && value == null){
                    throw new ProtobufSerializationException("Mandatory field with index %s in %s cannot be null"
                            .formatted(record.index(), object.getClass().getName()));
                }

                encodeField(output, record, value);
            }
            return output.buffer().toByteArray();
        } catch (ProtobufException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new ProtobufSerializationException("An unknown exception occurred while serializing", throwable);
        }
    }

    private void encodeField(ArrayOutputStream output, ProtobufField field, Object value) {
        if(value == null){
            return;
        }

        try {
            if(field.repeated()){
               try {
                   var singleField = ProtobufAnnotation.toNonRepeatedField(field);
                   var collection = (Collection<?>) value;
                   collection.forEach(entry -> encodeField(output, singleField, entry));
                   return;
               }catch (ClassCastException exception){
                   throw new RuntimeException("An error occurred while serializing %s: repeated fields should be wrapped in a collection".formatted(field), exception);
               }
            }
            
            switch (field.type()) {
                case BOOL -> output.writeBool(field.index(), (boolean) value);
                case STRING -> output.writeString(field.index(), (String) value);
                case BYTES -> output.writeByteArray(field.index(), (byte[]) value);
                case FLOAT -> output.writeFixed32(field.index(), Float.floatToRawIntBits((float) value));
                case DOUBLE -> output.writeFixed64(field.index(), Double.doubleToRawLongBits((double) value));
                case INT32, SINT32 -> output.writeInt32(field.index(), (int) value);
                case UINT32 -> output.writeUInt32(field.index(), (int) value);
                case FIXED32, SFIXED32 -> output.writeFixed32(field.index(), (int) value);
                case INT64, SINT64 -> output.writeInt64(field.index(), (long) value);
                case UINT64 -> output.writeUInt64(field.index(), (long) value);
                case FIXED64, SFIXED64 -> output.writeFixed64(field.index(), (long) value);
                default -> {
                    if (value instanceof Enum<?>) {
                        output.writeUInt64(field.index(), findEnumIndex(value));
                        return;
                    }

                    var message = writeMessage(value);
                    output.writeByteArray(field.index(), message);
                }
            }
        } catch (ClassCastException exception) {
            throw new RuntimeException("A field misreported its own type in a schema: %s".formatted(field), exception);
        }
    }

    private int findEnumIndex(Object object) {
        try {
            var method = object.getClass().getMethod("index");
            method.setAccessible(true);
            return (int) method.invoke(object);
        } catch (Throwable throwable) {
            return findEnumIndexFallback(object);
        }
    }

    private int findEnumIndexFallback(Object object) {
        try {
            var method = object.getClass().getMethod("ordinal");
            method.setAccessible(true);
            return (int) method.invoke(object);
        } catch (Throwable throwable) {
            throw new RuntimeException("An exception occurred while invoking the index method for the enum", throwable);
        }
    }
}