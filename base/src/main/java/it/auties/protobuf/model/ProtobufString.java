// While benchmarking ModernProtobuf against Google's implementation, I noticed that the latter performed much better when Strings were involved
// I remembered seeing some time ago that they were using a custom Utf8 decoder, so I thought they had achieved some incredible feat of engineering and left me in the dust
// After tracking down their implementation, available at https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/Utf8.java,
// I benchmarked it against the JDK's implementation and discovered that it was actually around 1.5-2x slower
// I used JDK 21, I'm sure the implementation in JDK 7/8, which is the lowest version they support as far as I remember, is much worse than what Google uses
// At this point I was almost certain that they just deferred the utf8 decoding to the message accessor, and sure enough when I invoked the latter in my tests the results flipped in our favour
// With this knowledge, I decided I might as well do the same thing, but here comes the problem: ModernProtobuf can't defer the decoding to the accessor because it doesn't control how that's implemented
// I than thought that the easy solution would be to create a lazy string implementation that extends java.lang.String and defers the decoding to the methods inherited from its super class that need that data
// Imagine my face when exactly one second after crafting this incredible plan I realized that String is final, so I can't extend it
// At this point I was faced with a huge problem: I'd like developers to use String as a type in their model, instead of having to use a custom type like ProtobufString, but there's no way for me to pass to the model's constructor a lazy string
// I thought some more about it and decided that having ProtobufString <-> String interoperability using a mixin would be good enough,
// but this solution is not perfect because a developer might use the String type in their model without realizing that this "disables" lazy string decoding
// This can be partially solved by making the compiler print a supportable warning telling the dev to consider the performance hit, but still this adds friction to the development process
// This is where the story ends in terms of what's done, but if you like over engineered solutions you might as well continue reading
//
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
// Sometimes we loose to the language designers, I'm sure that moments like this lead Google engineers to develop more than once new programming languages in house
// One day I'll probably do the same, just not right now because I feel like I still don't know enough about type systems and I'd hate to be clowned on like Go developers when they were asked about their interesting type system choices
//
// Will anyone ever read this? I don't know, but I hope that, if someone does, I can convey the sheer complexity of the platforms we build upon and how deep the rabbit hole for anything truly is

package it.auties.protobuf.model;

import it.auties.protobuf.stream.ProtobufOutputStream;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

@SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
public sealed abstract class ProtobufString {
    public static ProtobufString wrap(String string) {
        return new Value(string);
    }

    public static ProtobufString lazy(byte[] bytes, int offset, int length) {
        return new Bytes(bytes, offset, length);
    }

    public abstract void write(int field, ProtobufOutputStream outputStream);

    @Override
    public abstract String toString();

    public abstract int encodedLength();

    private static final class Bytes extends ProtobufString {
        private final byte[] buffer;
        private final int offset;
        private final int length;
        private String decoded;
        private Bytes(byte[] buffer, int offset, int length) {
            this.buffer = buffer;
            this.offset = offset;
            this.length = length;
        }


        public boolean equals(Object anObject) {
            return switch (anObject) {
                case String string -> Objects.equals(toString(), string);
                case Bytes lazyString -> Arrays.mismatch(buffer, lazyString.buffer) == -1;
                case Value wrappedString -> Objects.equals(toString(), wrappedString.value);
                case null, default -> false;
            };
        }

        @Override
        public String toString() {
            if(decoded != null) {
                return decoded;
            }

            synchronized (this) {
                return Objects.requireNonNullElseGet(decoded, () -> this.decoded = new String(buffer, offset, length, StandardCharsets.UTF_8));
            }
        }

        @Override
        public int hashCode() {
            var end = offset + length;
            var result = 1;
            for (var i = offset; i < end; i++) {
                result = 31 * result + buffer[i];
            }
            return result;
        }

        @Override
        public void write(int field, ProtobufOutputStream outputStream) {
            outputStream.writeBytes(field, buffer, offset, length);
        }

        @Override
        public int encodedLength() {
            return length;
        }
    }

    private static final class Value extends ProtobufString {
        private final String value;
        public Value(String value) {
            this.value = value;
        }

        public boolean equals(Object anObject) {
            return switch (anObject) {
                case String string -> Objects.equals(value, string);
                case Bytes lazyString -> Objects.equals(value, lazyString.toString());
                case Value wrappedString -> Objects.equals(value, wrappedString.value);
                case null, default -> false;
            };
        }

        @Override
        public void write(int field, ProtobufOutputStream outputStream) {
            outputStream.writeBytes(field, value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public int encodedLength() {
            var count = 0;
            for (int i = 0, len = value.length(); i < len; i++) {
                var ch = value.charAt(i);
                if (ch <= 0x7F) {
                    count++;
                } else if (ch <= 0x7FF) {
                    count += 2;
                } else if (Character.isHighSurrogate(ch)) {
                    count += 4;
                    ++i;
                } else {
                    count += 3;
                }
            }
            return count;
        }
    }
}
