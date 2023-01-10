package performance;

import it.auties.protobuf.serialization.stream.ArrayInputStream;
import lombok.SneakyThrows;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.util.HashMap;

public class SimpleParser {
    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T> Object decode(ArrayInputStream input, Class<T> clazz) {
        var results = new HashMap<>();
        while (true) {
            var tag = input.readTag();
            if (tag == 0) {
                break;
            }

            var number = tag >>> 3;
            var content = readFieldContent(input, tag);
            if (content == null) {
                break;
            }

            results.put(number, content);
        }

        return (T) ((MethodHandle) clazz.getField("CONSTRUCTOR").get(null)).invoke(
                results.get(1),
                results.get(2),
                results.get(3),
                results.get(4),
                results.get(5),
                results.get(6),
                results.get(7),
                results.get(8),
                results.get(9),
                results.get(10),
                results.get(11)
        );
    }

    private static Object readFieldContent(ArrayInputStream input, int tag) throws IOException {
        var type = tag & 7;
        return switch (type) {
            case 0 -> input.readInt64();
            case 1 -> input.readFixed64();
            case 5 -> input.readFixed32();
            default -> throw new RuntimeException();
        };
    }

}