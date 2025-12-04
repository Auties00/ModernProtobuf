package it.auties.protobuf.model;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * A sealed interface of all possible values for an unknown field encountered during Protobuf parsing.
 *
 * @see ProtobufWireType
 */
public sealed interface ProtobufUnknownValue {
    /**
     * Represents an unknown field value that has {@link ProtobufWireType#WIRE_TYPE_VAR_INT} wire type.
     *
     * @param value the decoded {@code long} value.
     */
    record VarInt(long value) implements ProtobufUnknownValue {

    }

    /**
     * Represents an unknown field value that has {@link ProtobufWireType#WIRE_TYPE_FIXED64} wire type.
     *
     * @param value the decoded {@code long} value.
     */
    record Fixed64(long value) implements ProtobufUnknownValue {

    }

    /**
     * Represents an unknown field value that has {@link ProtobufWireType#WIRE_TYPE_LENGTH_DELIMITED} wire type.
     */
    sealed interface LengthDelimited extends ProtobufUnknownValue {
        /**
         * Decodes the underlying byte data into a {@link String}.
         *
         * @return the decoded {@code String} value.
         */
        String asDecodedString();

        /**
         * Returns a lazy representation of the underlying byte data as a string.
         *
         * @return a {@code ProtobufLazyString} instance.
         */
        ProtobufLazyString asLazyString();

        /**
         * Represents length-delimited data stored as a raw byte array.
         *
         * @param value the raw byte array data.
         */
        record AsByteArray(byte[] value) implements LengthDelimited {
            /**
             * Constructs a new {@code Bytes} record, ensuring the value is not null.
             *
             * @param value the raw byte array data.
             */
            public AsByteArray {
                Objects.requireNonNull(value, "value cannot be null");
            }

            /**
             * Creates a new UTF-8 {@link String} from the underlying byte array.
             *
             * @return the decoded {@code String}.
             */
            @Override
            public String asDecodedString() {
                return new String(value, StandardCharsets.UTF_8);
            }

            /**
             * Returns a lazy string representation wrapping the byte array.
             *
             * @return a {@code ProtobufLazyString} instance.
             */
            @Override
            public ProtobufLazyString asLazyString() {
                return ProtobufLazyString.of(value);
            }
        }

        /**
         * Represents length-delimited data stored as a {@link ByteBuffer}.
         *
         * @param value the {@code ByteBuffer} containing the data.
         */
        record AsByteBuffer(ByteBuffer value) implements LengthDelimited {
            private static final ThreadLocal<CharsetDecoder> UTF8_DECODER = ThreadLocal.withInitial(() ->
                    StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPLACE)
                            .onUnmappableCharacter(CodingErrorAction.REPLACE));

            /**
             * Constructs a new {@code Buffer} record, ensuring the value is not null.
             *
             * @param value the {@code ByteBuffer} containing the data.
             */
            public AsByteBuffer {
                Objects.requireNonNull(value, "value cannot be null");
            }

            /**
             * Decodes the UTF-8 {@code ByteBuffer} contents into a {@link String}
             *
             * @return the decoded {@code String}.
             */
            @Override
            public String asDecodedString() {
                try {
                    var decoder = UTF8_DECODER.get();
                    decoder.reset();
                    // Assumes the ByteBuffer is already positioned correctly for reading
                    var decoded = decoder.decode(value.asReadOnlyBuffer());
                    return decoded.toString();
                }catch (CharacterCodingException _) {
                    // This should not happen since the error actions are set to REPLACE
                    throw new InternalError();
                }
            }

            /**
             * Returns a lazy string representation wrapping the ByteBuffer.
             *
             * @return a {@code ProtobufLazyString} instance.
             */
            @Override
            public ProtobufLazyString asLazyString() {
                return ProtobufLazyString.of(value);
            }
        }

        /**
         * Represents length-delimited data stored as a {@link MemorySegment}.
         *
         * @param value the {@code MemorySegment} containing the data.
         */
        record AsMemorySegment(MemorySegment value) implements LengthDelimited {
            /**
             * Constructs a new {@code AsMemorySegment} record, ensuring the value is not null.
             *
             * @param value the {@code MemorySegment} containing the data.
             */
            public AsMemorySegment {
                Objects.requireNonNull(value, "value cannot be null");
            }

            /**
             * Decodes the UTF-8 {@code ByteBuffer} contents into a {@link String}
             *
             * @return the decoded {@code String}.
             */
            @Override
            public String asDecodedString() {
                return value.getString(0, StandardCharsets.UTF_8);
            }

            /**
             * Returns a lazy string representation wrapping the ByteBuffer.
             *
             * @return a {@code ProtobufLazyString} instance.
             */
            @Override
            public ProtobufLazyString asLazyString() {
                return ProtobufLazyString.of(value);
            }
        }
    }

    /**
     * Represents an unknown field value that has {@link ProtobufWireType#WIRE_TYPE_START_OBJECT} wire type.
     */
    record Group(Map<Long, ProtobufUnknownValue> value) implements ProtobufUnknownValue {
        /**
         * Constructs a new {@code Group} record, ensuring the map is not null.
         *
         * @param value the map of field numbers to inner unknown values.
         */
        public Group {
            Objects.requireNonNull(value, "value cannot be null");
        }
    }

    /**
     * Represents an unknown field value that has {@link ProtobufWireType#WIRE_TYPE_FIXED32} wire type.
     *
     * @param value the decoded {@code int} value.
     */
    record Fixed32(int value) implements ProtobufUnknownValue {

    }
}