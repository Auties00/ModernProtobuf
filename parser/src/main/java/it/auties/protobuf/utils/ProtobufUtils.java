package it.auties.protobuf.utils;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import javax.lang.model.SourceVersion;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@UtilityClass
public class ProtobufUtils {
    public String getJavaClass(String protobufType, boolean repeated){
        if(protobufType.equals("string")){
            return repeated ? "List<String>" : "String";
        }

        if(protobufType.equals("bool")){
            return repeated ? "List<Boolean>" : "boolean";
        }

        if(protobufType.equals("double")){
            return repeated ? "List<Double>" : "double";
        }

        if(protobufType.equals("float")){
            return repeated ? "List<Float>" : "float";
        }

        if(protobufType.equals("bytes")){
            return repeated ? "List<ByteBuffer>" : "ByteBuffer";
        }

        if(protobufType.equals("int32") || protobufType.equals("uint32") || protobufType.equals("sint32") || protobufType.equals("fixed32") || protobufType.equals("sfixed32")){
            return repeated ? "List<Integer>" : "int";
        }

        if(protobufType.equals("int64") || protobufType.equals("uint64") || protobufType.equals("sint64") || protobufType.equals("fixed64") || protobufType.equals("sfixed64")){
            return repeated ? "List<Long>" : "long";
        }

        return protobufType;
    }
}
