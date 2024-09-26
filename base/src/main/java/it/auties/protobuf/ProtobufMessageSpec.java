package it.auties.protobuf;

import it.auties.protobuf.stream.ProtobufInputStream;

import java.util.HashMap;
import java.util.Map;

public class ProtobufMessageSpec {
    public static Map<Integer, Object> decode(byte[] protoInputStream) {
        return decode(new ProtobufInputStream(protoInputStream, 0, protoInputStream.length));
    }

    public static Map<Integer, Object> decode(ProtobufInputStream protoInputStream) {
        var result = new HashMap<Integer, Object>();
        while (protoInputStream.readTag()) {
            var key = protoInputStream.index();
            var value = protoInputStream.readUnknown(true);
            result.put(key, value);
        }
        return result;
    }
}
