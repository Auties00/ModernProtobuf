package it.auties.protobuf.encoder;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProtobufEncoder {
    public static byte[] encode(Object object) throws IOException, IllegalAccessException {
        return encode(object, false);
    }

    private static byte[] encode(Object object, boolean innerContext) throws IOException, IllegalAccessException {
        var output = new ArrayOutputStream(new ByteArrayOutputStream());
        if(object == null){
            return output.buffer().toByteArray();
        }

        for(var field : object.getClass().getDeclaredFields()){
            var notation = field.getAnnotation(JsonProperty.class);
            if(notation == null){
                continue;
            }

            var type = field.getType();
            field.setAccessible(true);
            var handle = field.get(object);
            if(handle == null){
                continue;
            }

            var number = Integer.parseInt(notation.value());
            if(type.equals(Long.TYPE) || type.equals(Double.TYPE)){
                output.writeUInt64(number, (long) handle);
            }else if(type.equals(Boolean.TYPE)){
                output.writeBool(number, (boolean) handle);
            }else if(type.equals(String.class)){
                output.writeBytes(number, ((String) handle).getBytes(StandardCharsets.UTF_8));
            }else if(type.equals(byte[].class)){
                output.writeByteArray(number, (byte[]) handle);
            }else if(type.equals(int.class)){
                output.writeFixed32(number, (int) handle);
            }else {
                output.writeTag(number, innerContext ? 2 : 3);
                output.writeBytesNoTag(encode(handle, innerContext));
            }
        }

        return output.buffer().toByteArray();
    }
}
