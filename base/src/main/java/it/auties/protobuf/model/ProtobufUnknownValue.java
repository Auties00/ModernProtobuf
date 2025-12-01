package it.auties.protobuf.model;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public sealed interface ProtobufUnknownValue {
    record VarInt(long value) implements ProtobufUnknownValue {

    }

    record Fixed64(long value) implements ProtobufUnknownValue {

    }

    sealed interface LengthDelimited extends ProtobufUnknownValue {
        String asDecodedString();
        ProtobufLazyString asLazyString();

        record Bytes(byte[] value) implements LengthDelimited {
            public Bytes {
                Objects.requireNonNull(value, "value cannot be null");
            }

            @Override
            public String asDecodedString() {
                return new String(value);
            }

            @Override
            public ProtobufLazyString asLazyString() {
                return ProtobufLazyString.of(value);
            }
        }

        record Buffer(ByteBuffer value) implements LengthDelimited {
            private static final ThreadLocal<CharsetDecoder> UTF8_DECODER = ThreadLocal.withInitial(() ->
                    StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPLACE)
                            .onUnmappableCharacter(CodingErrorAction.REPLACE));

            public Buffer {
                Objects.requireNonNull(value, "value cannot be null");
            }

            @Override
            public String asDecodedString() {
                try {
                    var decoder = UTF8_DECODER.get();
                    decoder.reset();
                    var decoded = decoder.decode(value);
                    return decoded.toString();
                }catch (CharacterCodingException _) {
                    throw new InternalError();
                }
            }

            @Override
            public ProtobufLazyString asLazyString() {
                return ProtobufLazyString.of(value);
            }
        }
    }

    record Group(Map<Long, ProtobufUnknownValue> value) implements ProtobufUnknownValue {
        public Group {
            Objects.requireNonNull(value, "value cannot be null");
        }
    }

    record Fixed32(int value) implements ProtobufUnknownValue {

    }
}
