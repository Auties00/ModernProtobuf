package it.auties.protobuf.serialization.performance;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.serialization.exception.ProtobufDeserializationException;
import it.auties.protobuf.serialization.model.WireType;
import it.auties.protobuf.serialization.stream.ArrayInputStream;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static it.auties.protobuf.serialization.model.WireType.*;

// Code style could be better in this class, but it's like this because it needs to be as performant as possible
@SuppressWarnings({"unchecked", "rawtypes"})
public class Protobuf<T extends ProtobufMessage> {
    private final Class<T> clazz;
    private final Map<Integer, Collection> repeatedFields;
    private final Map<Integer, ProtoField> properties;

    private Protobuf(Class<T> clazz){
        this.clazz = clazz;
        this.repeatedFields = new HashMap<>();
        this.properties = new HashMap<>();
        var declaredFields = clazz.getDeclaredFields();
        var declaredFieldsLength = declaredFields.length;
        var fields = clazz.getFields();
        var total = declaredFieldsLength + fields.length;
        for(var i = 0; i < total; i++){
            var field = i < declaredFieldsLength ? declaredFields[i] : fields[i];
            var annotation = Annotations.getAnnotation(field, ProtobufProperty.class);
            if(annotation != null) {
                properties.put((Integer) annotation.get("index"), new ProtoField(field, annotation));
            }
        }
    }

    private record ProtoField(Field field, Map<String, Object> properties){}

    public static <T extends ProtobufMessage> T readMessage(byte[] message, Class<T> clazz) {
        if(message == null){
            return null;
        }

        var decoder = new Protobuf<>(clazz);
        return decoder.decode(message);
    }

    public static byte[] writeMessage(ProtobufMessage object) {
        if(object == null){
            return null;
        }

        var encoder = new Protobuf<>(object.getClass());
        return encoder.encode(object);
    }

    private T decode(byte[] bytes) {
       try {
           var input = new ArrayInputStream(bytes);
           var builder = MethodHandles.publicLookup()
                   .findStatic(clazz, "builder", MethodType.methodType(Object.class))
                   .invokeExact();
           var builderType = builder.getClass();
           var repeatedMatches = false;
           while (true) {
               var tag = input.readTag();
               if (tag == 0) {
                   break;
               }

               var index = tag >>> 3;
               if (index == 0) {
                   throw ProtobufDeserializationException.invalidTag(tag);
               }

               var field = properties.get(index);
               var fieldType = field.field.getType();
               var result = readFieldContent(input, tag, field);
               if(result == null){
                   continue;
               }

               if((boolean) field.properties.getOrDefault("repeated", false)){
                   repeatedMatches = true;
                   var repeatedWrapper = repeatedFields.computeIfAbsent(index, ignored -> createCollection(fieldType));
                   repeatedWrapper.add(result);
               }else {
                   MethodHandles.privateLookupIn(builderType, MethodHandles.lookup())
                           .findSetter(builderType, field.field.getName(), field.field.getType())
                           .invoke(builder, result);
               }
           }

           if(repeatedMatches) {
               // TODO: Repeated messages
           }

           return (T) MethodHandles.publicLookup()
                   .findVirtual(builderType, "build", MethodType.methodType(clazz))
                   .invoke(builder);
       }catch (Throwable throwable){
           throw new ProtobufDeserializationException("Cannot read message", throwable);
       }
    }

    private static Collection<?> createCollection(Class<?> fieldType){
        try {
            return (Collection<?>) fieldType.getConstructor().newInstance();
        }catch (ReflectiveOperationException exception){
            throw new NoSuchElementException("Cannot find a no-args constructor for class %s".formatted(fieldType.getName()));
        }
    }

    private Object readFieldContent(ArrayInputStream input, int tag, ProtoField field) {
        var type = field != null ? (ProtobufType) field.properties.get("type") : null;
        return switch (tag & 7) {
            case WIRE_TYPE_VAR_INT -> {
                var value = input.readInt64();
                yield switch (type) {
                    case INT32, SINT32, UINT32, MESSAGE -> (int) value;
                    case INT64, SINT64, UINT64 -> value;
                    case BOOL -> value == 1;
                    case null -> null;
                    default -> throw new IllegalStateException("Unexpected value: " + type);
                };
            }
            case WIRE_TYPE_FIXED64 -> {
                var value = input.readFixed64();
                if(field == null){
                    yield null;
                }else if (type == ProtobufType.DOUBLE) {
                    yield Double.longBitsToDouble(value);
                }else {
                    yield value;
                }
            }
            case WIRE_TYPE_LENGTH_DELIMITED -> {
                var read = input.readBytes();
                if(field == null){
                    yield null;
                } else if((boolean) field.properties.get("packed")){
                    var stream = new ArrayInputStream(read);
                    yield switch (type){
                        case FIXED32, SFIXED32 -> stream.readFixed32();
                        case INT32, SINT32, UINT32 -> stream.readInt32();
                        case FLOAT -> Float.intBitsToFloat(stream.readFixed32());
                        case FIXED64, SFIXED64 -> stream.readFixed64();
                        case INT64, SINT64, UINT64  -> stream.readInt64();
                        case DOUBLE -> Double.longBitsToDouble(stream.readFixed64());
                        default -> throw new IllegalStateException("Unexpected value: " + type);
                    };
                } else {
                    yield switch (type) {
                        case BYTES -> read;
                        case STRING -> new String(read, StandardCharsets.UTF_8);
                        case MESSAGE -> {
                            var implementation = (Class<?>) field.properties.get("implementation");
                            var decoder = new Protobuf(implementation);
                            yield decoder.decode(read);
                        }
                        default -> throw new IllegalStateException("Unexpected value: " + type);
                    };
                }
            }
            case WIRE_TYPE_EMBEDDED_MESSAGE -> {
                var read = input.readBytes();
                if(field == null || read == null){
                    yield null;
                }else {
                    var implementation = (Class<?>) field.properties.get("implementation");
                    var decoder = new Protobuf(implementation);
                    yield decoder.decode(read);
                }
            }
            case WireType.WIRE_TYPE_FIXED32 -> {
                var value = input.readFixed32();
                if(field == null){
                    yield null;
                }else if (type == ProtobufType.FLOAT) {
                    yield Float.intBitsToFloat(value);
                } else {
                    yield value;
                }
            }
            case WireType.WIRE_TYPE_END_OBJECT -> null;
            default -> throw new ProtobufDeserializationException("Protocol message had invalid wire type");
        };
    }

    private byte[] encode(Object object) {
        throw new UnsupportedOperationException();
    }
}