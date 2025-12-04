// While benchmarking ModernProtobuf against Google's implementation, I noticed that the latter performed much better when Strings were involved
// I remembered seeing some time ago that they were using a custom Utf8 decoder, so I thought they had achieved some incredible feat of engineering and left me in the dust
// After tracking down their implementation, available at https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/Utf8.java,
// I benchmarked it against the JDK's implementation and discovered that it was actually around 1.5-2x slower
// I used JDK 21, but I'm sure the implementation in JDK 7/8, which is the lowest version they support as far as I remember, is much worse than what Google uses
// At this point I was almost certain that they just deferred the utf8 decoding to the message accessor, and sure enough when I invoked the latter in my tests the results flipped in our favour
// With this knowledge, I decided I might as well do the same thing, but here comes the problem: ModernProtobuf can't defer the decoding to the accessor because it doesn't control how that's implemented
// I than thought that the easy solution would be to create a lazy string implementation that extends java.lang.String and defers the decoding to the methods inherited from its super class that need that data
// Imagine my face when exactly one second after crafting this incredible plan I realized that String is final, so I can't extend it
// At this point I was faced with a huge problem: I'd like developers to use String as a type in their model, instead of having to use a custom type like ProtobufString, but there's no way for me to pass to the model's constructor a lazy string
// I thought some more about it and decided that having ProtobufString <-> String interoperability using a mixin would be good enough,
// but this solution is not perfect because a developer might use the String type in their model without realizing that this "disables" lazy string decoding
// This can be partially solved by making the compiler print a supportable warning telling the dev to consider the performance hit, but still this adds friction to the development process
// While thinking about possible solutions, I remembered when, around five years ago, I used Java agents to instrument Minecraft's server code using bytecode manipulation to alter the physics of the game
// I then figured that I could theoretically craft a class that is theoretically eligible to extend java.lang.String(i.e. all the methods from the super class are inherited and there are no conflicts)
// and then at runtime, using a java agent, instrument java.lang.String to remove the final modifier and modify the crafted class' bytecode to extend java.lang.String
// There's a problem though: to load a java agent you need to specify it in the runtime args of your java application which is a nightmare because it could break entire applications that don't know that one of their dependencies requires an agent
// I also remembered though that I had read that it was possible to attach the JVM at runtime, load your agent and detach from it, but I thought I might be hallucinating things or thinking about the days before module encapsulation
// Imagine my face pt2 when I found the article I read five years ago(https://web.archive.org/web/20141014195801/http://dhruba.name/2010/02/07/creation-dynamic-loading-and-instrumentation-with-javaagents/) and figured that it still worked
// So now all the pieces are there to implement this thing, and I don't even think that it's a crime against the platform's integrity considering that even the JDK uses bytecode manipulation (if they do it, we can do it remember (don't hold me to this quote))
// The reason why I haven't implemented this crazy overengineered solution yet is not because it's over engineering, but because there's still a problem in our pipeline: compile time
// As the lazy string implementation class doesn't formally extend String at compile time, the readString method in ProtobufInputStream will return a value that cannot be legally passed to the message's constructor in the generated code
// There is however a way to fix this, which is to write a compiler plugin that hooks the Javac error handling system to suppress the error if it comes from our annotation processor
// This is not me talking about ideas: I've already coded the same thing for Reified, a previous compiler project I worked on that aimed to bring reification to Java, to allow reified generics array initialization
// At this point though, I have to consider that I want ModernProtobuf to be a production ready idea, so I can't be shipping a java agent, annotation processor and compiler plugin that use bytecode manipulation just for developers to write String instead of ProtobufString

package it.auties.protobuf.model;

import it.auties.protobuf.stream.ProtobufOutputStream;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A model that represents an underlying String value
 * The hash code implementation is based on the encoded byte[] representation so that it matches encoded and decoded values
 * This means that the hashCode produced by a ProtobufLazyString and String will NOT match
 */
@SuppressWarnings("all")
public sealed abstract class ProtobufLazyString implements CharSequence {
    protected final StableValue<String> decoded;

    protected ProtobufLazyString() {
        this.decoded = StableValue.of();
    }

    public static ProtobufLazyString of(byte[] bytes) {
        return of(bytes, 0, bytes.length);
    }

    public static ProtobufLazyString of(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.checkFromIndexSize(offset, length, bytes.length);
        return new ByteArrayBacked(bytes, offset, length);
    }

    public static ProtobufLazyString of(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        return new ByteBufferBacked(buffer);
    }

    public static ProtobufLazyString of(MemorySegment segment) {
        Objects.requireNonNull(segment, "segment must not be null");
        return new MemorySegmentBacked(segment);
    }

    public abstract int encodedLength();

    public abstract void writeTo(long fieldIndex, ProtobufOutputStream<?> stream);

    @Override
    public char charAt(int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    protected static int computeUtf8CharCount(byte[] bytes, int offset, int length) {
        var index = offset;
        var end = offset + length;
        var charCount = 0;
        while (index < end) {
            var b = bytes[index];
            if ((b & 0x80) == 0) {
                index++;
                charCount++;
            } else if ((b & 0xE0) == 0xC0) {
                index += 2;
                charCount++;
            } else if ((b & 0xF0) == 0xE0) {
                index += 3;
                charCount++;
            } else {
                index += 4;
                charCount += 2;
            }
        }
        return charCount;
    }

    protected static boolean equals(byte[] bytes, int offset, int length, ByteBuffer buffer) {
        if (buffer.hasArray()) {
            return Arrays.mismatch(bytes, offset, offset + length, buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.arrayOffset() + buffer.limit()) == -1;
        } else {
            return buffer.mismatch(ByteBuffer.wrap(bytes, offset, length)) == -1;
        }
    }

    protected static int hashCode(byte[] source, int offset, int length) {
        var result = 1;
        for (var i = offset; i < offset + length; i++) {
            result = 31 * result + source[i];
        }
        return result;
    }

    private static final class ByteArrayBacked extends ProtobufLazyString {
        private final byte[] bytes;
        private final int offset;
        private final int length;

        private ByteArrayBacked(byte[] bytes, int offset, int length) {
            this.bytes = bytes;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public int encodedLength() {
            return length;
        }

        @Override
        public int length() {
            if (decoded.isSet()) {
                return decoded.orElseThrow().length();
            } else {
                return computeUtf8CharCount(bytes, offset, length);
            }
        }

        @Override
        public void writeTo(long fieldIndex, ProtobufOutputStream<?> stream) {
            Objects.requireNonNull(stream, "stream must not be null");
            stream.writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            stream.writeRawFixedInt32(length);
            stream.writeRawBytes(bytes, offset, length);
        }

        @Override
        public String toString() {
            return decoded.orElseSet(() -> new String(bytes, offset, length, StandardCharsets.UTF_8));
        }

        @Override
        public int hashCode() {
            return hashCode(bytes, offset, length);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || switch (obj) {
                case ByteArrayBacked that when length == that.length -> Arrays.mismatch(bytes, offset, offset + length, that.bytes, that.offset, that.offset + that.length) == -1;
                case ByteBufferBacked that when length == that.buffer.remaining() -> equals(bytes, offset, length, that.buffer);
                case null, default -> false;
            };
        }

    }

    private static final class ByteBufferBacked extends ProtobufLazyString {
        private static final ThreadLocal<CharsetDecoder> UTF8_DECODER = ThreadLocal.withInitial(() ->
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE));

        private final ByteBuffer buffer;

        private ByteBufferBacked(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public int encodedLength() {
            return buffer.remaining();
        }

        @Override
        public int length() {
            if (decoded.isSet()) {
                return decoded.orElseThrow().length();
            } else if (buffer.hasArray()) {
                return computeUtf8CharCount(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            } else {
                var index = 0;
                var length = buffer.remaining();
                var position = buffer.position();
                var charCount = 0;
                while (index < length) {
                    var b = buffer.get(position + index);
                    if ((b & 0x80) == 0) {
                        index++;
                        charCount++;
                    } else if ((b & 0xE0) == 0xC0) {
                        index += 2;
                        charCount++;
                    } else if ((b & 0xF0) == 0xE0) {
                        index += 3;
                        charCount++;
                    } else {
                        index += 4;
                        charCount += 2;
                    }
                }
                return charCount;
            }
        }

        @Override
        public void writeTo(long fieldIndex, ProtobufOutputStream<?> stream) {
            Objects.requireNonNull(stream, "stream must not be null");
            stream.writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            stream.writeRawFixedInt32(buffer.remaining());
            stream.writeRawBuffer(buffer);
        }

        @Override
        public String toString() {
            return decoded.orElseSet(() -> {
                var decoder = UTF8_DECODER.get();
                decoder.reset();
                try {
                    var charBuffer = decoder.decode(buffer.duplicate());
                    return charBuffer.toString();
                } catch (CharacterCodingException _) {
                    throw new InternalError();
                }
            });
        }

        @Override
        public int hashCode() {
            if (buffer.hasArray()) {
                return hashCode(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            } else {
                var result = 1;
                var position = buffer.position();
                var length = buffer.remaining();
                for (var i = 0; i < length; i++) {
                    result = 31 * result + buffer.get(position + i);
                }
                return result;
            }
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || switch (obj) {
                case ByteBufferBacked that when buffer.remaining() == that.buffer.remaining() -> buffer.mismatch(that.buffer) == -1;
                case ByteArrayBacked that when buffer.remaining() == that.length -> equals(that.bytes, that.offset, that.length, buffer);
                case null, default -> false;
            };
        }
    }

    private static final class MemorySegmentBacked extends ProtobufLazyString {
        private final MemorySegment segment;

        private MemorySegmentBacked(MemorySegment segment) {
            if(segment.byteSize() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Segment too large");
            } else {
                this.segment = segment;
            }
        }

        @Override
        public int encodedLength() {
            return (int) segment.byteSize();
        }

        @Override
        public int length() {
            if (decoded.isSet()) {
                return decoded.orElseThrow().length();
            } else {
                var index = 0;
                var length = (int) segment.byteSize();
                var position = 0;
                var charCount = 0;
                while (index < length) {
                    var b = segment.getAtIndex(ValueLayout.OfByte.JAVA_BYTE, 0);
                    if ((b & 0x80) == 0) {
                        index++;
                        charCount++;
                    } else if ((b & 0xE0) == 0xC0) {
                        index += 2;
                        charCount++;
                    } else if ((b & 0xF0) == 0xE0) {
                        index += 3;
                        charCount++;
                    } else {
                        index += 4;
                        charCount += 2;
                    }
                }
                return charCount;
            }
        }

        @Override
        public void writeTo(long fieldIndex, ProtobufOutputStream<?> stream) {
            Objects.requireNonNull(stream, "stream must not be null");
            stream.writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            stream.writeRawFixedInt32((int) segment.byteSize());
            stream.writeRawSegment(segment);
        }

        @Override
        public String toString() {
            return decoded.orElseSet(() -> segment.getString(0, StandardCharsets.UTF_8));
        }

        @Override
        public int hashCode() {
            return segment.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || switch (obj) {
                case ByteBufferBacked that when segment.remaining() == that.buffer.remaining() -> segment.mismatch(that.buffer) == -1;
                case ByteArrayBacked that when segment.remaining() == that.length -> equals(that.bytes, that.offset, that.length, segment);
                case null, default -> false;
            };
        }
    }
}