
package it.auties.protobuf;

import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
     * @see ProtobufInputStream#readUnknown()
     */
    public static Map<Long, Object> decode(ProtobufInputStream protoInputStream) {
        Objects.requireNonNull(protoInputStream, "The input stream cannot be null");
        var result = new HashMap<Long, Object>();
        while (protoInputStream.readTag()) {
            var key = protoInputStream.index();
            var value = protoInputStream.readUnknown();
            if(value instanceof ByteBuffer buffer) {
                var position = buffer.position();
                try { // Maybe it's an embedded message
                    value = decode(ProtobufInputStream.fromBuffer(buffer));
                }catch (ProtobufDeserializationException ignored) {
                    // It wasn't an embedded message
                    buffer.position(position); // Reset the position
                    try {
                        value = StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(buffer);
                    } catch (CharacterCodingException e) {
                        // It's actually an array of bytes
                        buffer.position(position); // Reset the position
                    }
                }
            }
            result.put(key, value);
        }
        return result;
    }
}