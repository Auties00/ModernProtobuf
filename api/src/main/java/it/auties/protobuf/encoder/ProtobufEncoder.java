package it.auties.protobuf.encoder;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProtobufEncoder {
    public static byte[] encode(Object object) throws IOException, IllegalAccessException {
        var output = new ArrayOutputStream(new ByteArrayOutputStream());
        if(object == null){
            return output.buffer().toByteArray();
        }

        for(var field : object.getClass().getDeclaredFields()){
            var notation = field.getAnnotation(JsonProperty.class);
            if(notation == null){
                continue;
            }

            field.setAccessible(true);
            var handle = field.get(object);
            if(!isValidField(handle, notation.required())){
                continue;
            }

            var number = Integer.parseInt(notation.value());
            if(handle instanceof Long longHandle){
                output.writeUInt64(number, longHandle);
            }else if(handle instanceof Double doubleHandle){
                output.writeFixed64(number, Double.doubleToRawLongBits(doubleHandle));
            }else if(handle instanceof Boolean booleanHandle){
                output.writeBool(number, booleanHandle);
            }else if(handle instanceof String strHandle){
                output.writeBytes(number, strHandle.getBytes(StandardCharsets.UTF_8));
            }else if(handle instanceof byte[] bytesHandle){
                output.writeByteArray(number, bytesHandle);
            }else if(handle instanceof Integer intHandle){
                output.writeFixed32(number, intHandle);
            }else if(handle instanceof Enum<?>){
                output.writeUInt64(number, findEnumIndex(handle));
            }else {
                output.writeTag(number, 2);
                output.writeBytesNoTag(encode(handle));
            }
        }

        return output.buffer().toByteArray();
    }

    private static boolean isValidField(Object handle, boolean required){
        if(required){
            return true;
        }

        if(handle instanceof Number num){
            return num.intValue() != 0;
        }

        if(handle instanceof Boolean bool){
            return bool;
        }

        return handle != null;
    }

    private static int findEnumIndex(Object object){
        try {
            var indexField = object.getClass().getDeclaredField("index");
            indexField.setAccessible(true);
            return (int) indexField.get(object);
        }catch (Exception e){
            throw new RuntimeException("Cannot extract index value from index", e);
        }
    }
}
