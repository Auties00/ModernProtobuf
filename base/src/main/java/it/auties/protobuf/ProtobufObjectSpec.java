
package it.auties.protobuf;

import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.model.ProtobufUnknownValue;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A utility class for decoding Protocol Buffer messages into a generic map structure.
 * <p>
 * This class provides methods to decode Protocol Buffer encoded data without requiring
 * a specific message schema or compiled protobuf classes. The decoded data is returned
 * as a map where keys are field numbers and values are the decoded field values.
 * <p>
 * This is particularly useful for:
 * <ul>
 *   <li>Inspecting unknown or dynamic protobuf messages</li>
 *   <li>Debugging protobuf data without access to the original .proto definition</li>
 *   <li>Generic protobuf message processing</li>
 * </ul>
 * <p>
 *     <font color="red">
 *         This class is extremely slow: use it only when necessary.
 *     </font>
 * <p>
 */
public final class ProtobufObjectSpec {
    /**
     * Decodes a Protocol Buffer message from a ProtobufInputStream.
     * This method is extremely slow: use it only when deserializing dynamic messages.
     * <p>
     * This method reads from the provided input stream and decodes all available
     * protobuf fields into a map structure. Each field is read according to its
     * wire type and stored with its field number as the key.
     * <p>
     * <p>
     *     <font color="red">
     *         This method is extremely slow: use it only when necessary.
     *     </font>
     * <p>
     *
     * @param protoInputStream the input stream containing the encoded Protocol Buffer data
     * @return a map where keys are field numbers (Integer) and values are the decoded field values (Object).
     * @throws NullPointerException if the input stream is null
     * @throws ProtobufDeserializationException if the data cannot be decoded
     *
     * @see ProtobufInputStream#readUnknownProperty()
     */
    public static Map<Long, ProtobufUnknownValue> decode(ProtobufInputStream protoInputStream) {
        Objects.requireNonNull(protoInputStream, "The input stream cannot be null");
        var result = new HashMap<Long, ProtobufUnknownValue>();
        while (protoInputStream.readPropertyTag()) {
            var key = protoInputStream.propertyIndex();
            var value = decodeValue(protoInputStream);
            result.put(key, value);
        }
        return result;
    }

    private static ProtobufUnknownValue decodeValue(ProtobufInputStream stream) {
        var value = stream.readUnknownProperty();
        return switch (value) {
            case ProtobufUnknownValue.LengthDelimited.AsByteArray(var bytes) -> {
                try { // Maybe it's an embedded message
                    yield decodeValue(ProtobufInputStream.fromBytes(bytes));
                }catch (ProtobufDeserializationException _) {
                    // It wasn't an embedded message
                    yield value;
                }
            }

            case ProtobufUnknownValue.LengthDelimited.AsByteBuffer(var buffer) -> {
                var position = buffer.position();
                try { // Maybe it's an embedded message
                    yield decodeValue(ProtobufInputStream.fromBuffer(buffer));
                }catch (ProtobufDeserializationException _) {
                    buffer.position(position); // Reset the position
                    // It wasn't an embedded message
                    yield value;
                }
            }
            default -> value;
        };
    }
}