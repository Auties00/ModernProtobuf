package it.auties.protobuf.decoder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public record ProtobufDecoder<T>(Class<? extends T> modelClass) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).registerModule(new Jdk8Module());

    public static <T> ProtobufDecoder<T> forType(Class<? extends T> modelClass){
        return new ProtobufDecoder<>(modelClass);
    }

    public T decode(byte[] input) throws IOException {
        return OBJECT_MAPPER.convertValue(decode(new ArrayInputStream(input)), modelClass());
    }

    public String decodeAsJson(byte[] input) throws IOException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(decode(new ArrayInputStream(input)));
    }

    private Map<Integer, Object> decode(ArrayInputStream input) throws IOException {
        var results = new ArrayList<Map.Entry<Integer, Object>>();
        while(true) {
            var tag = input.readTag();
            if(tag == 0){
                break;
            }

            var current = parseUnknownField(input, tag);
            if(current.isEmpty()){
                break;
            }

            results.add(current.get());
        }

        input.checkLastTagWas(0);
        return results.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Arrays::asList));
    }

    private Optional<Map.Entry<Integer, Object>> parseUnknownField(ArrayInputStream input, int tag) throws IOException{
        var number = tag >>> 3;
        var type = tag & 7;
        var content = switch(type) {
            case 0 -> input.readInt64();
            case 1 -> input.readFixed64();
            case 2 -> readGroupOrString(input);
            case 3 -> decode(input);
            case 4 -> null;
            case 5 -> input.readFixed32();
            default -> throw new InvalidProtocolBufferException.InvalidWireTypeException("Protocol message tag had invalid wire type.");
        };

        return Optional.ofNullable(content).map(parsed -> Map.entry(number, parsed));
    }

    private Object readGroupOrString(ArrayInputStream input) throws IOException {
        var read = input.readBytes();
        try {
            return decode(new ArrayInputStream(read));
        } catch (InvalidProtocolBufferException var4) {
            return escapeBytes(read);
        }
    }

    private String escapeBytes(byte[] input) {
        var builder = new StringBuilder(input.length);
        for(var b : input){
            switch (b) {
                case 0x07 -> builder.append("\\a");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case 0x0b -> builder.append("\\v");
                case '\\' -> builder.append("\\\\");
                case '\'' -> builder.append("\\'");
                case '"'  -> builder.append("\\\"");
                default -> {
                    if (b >= 0x20 && b <= 0x7e) {
                        builder.append((char) b);
                    } else {
                        builder.append('\\');
                        builder.append((char) ('0' + ((b >>> 6) & 3)));
                        builder.append((char) ('0' + ((b >>> 3) & 7)));
                        builder.append((char) ('0' + (b & 7)));
                    }
                }
            }
        }

        return builder.toString();
    }
}
