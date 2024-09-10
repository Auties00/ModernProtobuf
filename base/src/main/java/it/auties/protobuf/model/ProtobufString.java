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
// I also considered ByteBuddy, but it obviously can't magically make the JVM accept a class that extends a final class, we still need an agent
//
// FINAL DESIGN DECISION
// Implement all String methods in a sealed classes that specializes already deserialized string and string that can be deserialized if needed
// Don't provide default mixin as ProtobufString offers all the String features
// Hash might not be the same for a wrap

package it.auties.protobuf.model;

import it.auties.protobuf.stream.ProtobufOutputStream;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A model that represents an underlying String value
 * The underlying value can either be decoded, as described by {@link Value}, or encoded, as described by {@link Bytes}
 * The hash code implementation is based on the encoded byte[] representation so that it matches encoded and decoded values
 * This means that the hashCode produced by a ProtobufString and String will NOT match
 * All methods from String are also available in ProtobufString, if you need to pass it to a method that takes a String use {@link ProtobufString#toString()}, or modify the method to accept a {@link CharSequence}
 */
@SuppressWarnings({
        "EqualsWhichDoesntCheckParameterClass", // Equality is implemented differently
        "unused",  // All methods are needed, consider design explanation in the class header
        "ReplaceNullCheck", // Don't want to use Objects.requireNonNullElseGet as old style if check is faster
        "NullableProblems" // Don't want to provide annotations for null properties
})
public sealed abstract class ProtobufString implements CharSequence {
    public static ProtobufString wrap(String string) {
        return new Value(string);
    }

    public static ProtobufString lazy(byte[] bytes, int offset, int length) {
        return new Bytes(bytes, offset, length);
    }

    public static ProtobufString lazy(ByteBuffer buffer) {
        return new Buffer(buffer);
    }

    @Override
    public abstract boolean equals(Object anObject);

    public abstract void write(int field, ProtobufOutputStream outputStream);

    public abstract String toString();

    public abstract int hashCode();

    public abstract int encodedLength();

    public abstract int length();

    public abstract char charAt(int index);

    public abstract CharSequence subSequence(int start, int end);

    public abstract boolean contentEquals(CharSequence cs);

    public abstract boolean equalsIgnoreCase(String anotherString);

    public abstract boolean regionMatches(int toffset, String other, int ooffset, int len);

    public abstract boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len);

    public abstract boolean startsWith(String prefix, int toffset);

    public abstract boolean startsWith(String prefix);

    public abstract boolean endsWith(String suffix);

    public abstract int indexOf(int ch);

    public abstract int indexOf(int ch, int fromIndex);

    public abstract int lastIndexOf(int ch);

    public abstract int lastIndexOf(int ch, int fromIndex);

    public abstract int indexOf(String str);

    public abstract int indexOf(String str, int fromIndex);

    public abstract int lastIndexOf(String str);

    public abstract int lastIndexOf(String str, int fromIndex);

    public abstract String substring(int beginIndex);

    public abstract String substring(int beginIndex, int endIndex);

    public abstract String concat(String str);

    public abstract String replace(char oldChar, char newChar);

    public abstract boolean matches(String regex);

    public abstract boolean contains(CharSequence s);

    public abstract String replaceFirst(String regex, String replacement);

    public abstract String replaceAll(String regex, String replacement);

    public abstract String[] split(String regex, int limit);

    public abstract String[] split(String regex);

    public abstract String toLowerCase();

    public abstract String toLowerCase(Locale locale);

    public abstract String toUpperCase();

    public abstract String toUpperCase(Locale locale);

    public abstract String trim();

    public abstract char[] toCharArray();

    public abstract byte[] getBytes();

    public abstract byte[] getBytes(String charsetName) throws UnsupportedEncodingException;

    public abstract byte[] getBytes(Charset charset);

    public abstract void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin);

    public abstract boolean isEmpty();

    public abstract int codePointAt(int index);

    public abstract int codePointBefore(int index);

    public abstract int codePointCount(int beginIndex, int endIndex);

    public abstract int offsetByCodePoints(int index, int codePointOffset);

    public abstract void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin);

    public abstract boolean contentEquals(StringBuffer sb);

    public abstract int indexOf(int ch, int beginIndex, int endIndex);

    public abstract int indexOf(String str, int beginIndex, int endIndex);

    public abstract String replace(CharSequence target, CharSequence replacement);

    public abstract String[] splitWithDelimiters(String regex, int limit);

    public abstract String strip();

    public abstract String stripLeading();

    public abstract String stripTrailing();

    public abstract boolean isBlank();

    public abstract Stream<String> lines();

    public abstract String indent(int n);

    public abstract String stripIndent();

    public abstract String translateEscapes();

    public abstract <R> R transform(Function<? super String, ? extends R> f);

    public abstract IntStream chars();

    public abstract IntStream codePoints();

    public abstract String formatted(Object... args);

    public abstract String intern();

    public abstract String repeat(int count);

    public abstract Optional<String> describeConstable();

    public abstract String resolveConstantDesc(MethodHandles.Lookup lookup);

    private static void assertUtf8(String charset) {
        if (charset == null || !charset.equals("UTF-8")) {
            throw new UnsupportedOperationException();
        }
    }

    static boolean equals(ByteBuffer buffer, byte[] array, int arrayOffset, int arrayLength) {
        if(buffer.remaining() != arrayLength) {
            return false;
        }

        var bufferPosition = buffer.position();
        for(var i = 0; i < arrayLength; i++) {
            if(buffer.get(bufferPosition + i) != array[arrayOffset + i]) {
                return false;
            }
        }

        return true;
    }

    private static final class Bytes extends ProtobufString {
        private final byte[] bytes;
        private final int offset;
        private final int length;
        private String decoded;
        private Bytes(byte[] bytes, int offset, int length) {
            this.bytes = bytes;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public boolean equals(Object anObject) {
            return switch (anObject) {
                case Buffer lazyString -> equals(lazyString.buffer, bytes, offset, length);
                case Bytes lazyString -> Arrays.mismatch(bytes, offset, offset + length, lazyString.bytes, lazyString.offset, lazyString.offset + lazyString.length) == -1;
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
                if(decoded != null) {
                    return decoded;
                }

                return this.decoded = new String(bytes, offset, length, StandardCharsets.UTF_8);
            }
        }

        @Override
        public int hashCode() {
            var end = offset + length;
            var result = 1;
            for (var i = offset; i < end; i++) {
                result = 31 * result + bytes[i];
            }
            return result;
        }

        @Override
        public void write(int field, ProtobufOutputStream outputStream) {
            outputStream.writeBytes(field, bytes, offset, length);
        }

        @Override
        public int encodedLength() {
            return length;
        }

        @Override
        public int length() {
            return toString().length();
        }

        @Override
        public char charAt(int index) {
            return toString().charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return toString().subSequence(start, end);
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return toString().contentEquals(cs);
        }

        @Override
        public boolean equalsIgnoreCase(String anotherString) {
            return toString().equalsIgnoreCase(anotherString);
        }

        @Override
        public boolean regionMatches(int toffset, String other, int ooffset, int len) {
            return toString().regionMatches(toffset, other, ooffset, len);
        }

        @Override
        public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
            return toString().regionMatches(ignoreCase, toffset, other, ooffset, len);
        }

        @Override
        public boolean startsWith(String prefix, int toffset) {
            return toString().startsWith(prefix, toffset);
        }

        @Override
        public boolean startsWith(String prefix) {
            return toString().startsWith(prefix);
        }

        @Override
        public boolean endsWith(String suffix) {
            return toString().endsWith(suffix);
        }

        @Override
        public int indexOf(int ch) {
            return toString().indexOf(ch);
        }

        @Override
        public int indexOf(int ch, int fromIndex) {
            return toString().indexOf(ch, fromIndex);
        }

        @Override
        public int lastIndexOf(int ch) {
            return toString().lastIndexOf(ch);
        }

        @Override
        public int lastIndexOf(int ch, int fromIndex) {
            return toString().lastIndexOf(ch, fromIndex);
        }

        @Override
        public int indexOf(String str) {
            return toString().indexOf(str);
        }

        @Override
        public int indexOf(String str, int fromIndex) {
            return toString().indexOf(str, fromIndex);
        }

        @Override
        public int lastIndexOf(String str) {
            return toString().lastIndexOf(str);
        }

        @Override
        public int lastIndexOf(String str, int fromIndex) {
            return toString().lastIndexOf(str, fromIndex);
        }

        @Override
        public String substring(int beginIndex) {
            return toString().substring(beginIndex);
        }

        @Override
        public String substring(int beginIndex, int endIndex) {
            return toString().substring(beginIndex, endIndex);
        }

        @Override
        public String concat(String str) {
            return toString().concat(str);
        }

        @Override
        public String replace(char oldChar, char newChar) {
            return toString().replace(oldChar, newChar);
        }

        @Override
        public boolean matches(String regex) {
            return toString().matches(regex);
        }

        @Override
        public boolean contains(CharSequence s) {
            return toString().contains(s);
        }

        @Override
        public String replaceFirst(String regex, String replacement) {
            return toString().replaceFirst(regex, replacement);
        }

        @Override
        public String replaceAll(String regex, String replacement) {
            return toString().replaceAll(regex, replacement);
        }

        @Override
        public String[] split(String regex, int limit) {
            return toString().split(regex, limit);
        }

        @Override
        public String[] split(String regex) {
            return toString().split(regex);
        }

        @Override
        public String toLowerCase() {
            return toString().toLowerCase();
        }

        @Override
        public String toLowerCase(Locale locale) {
            return toString().toLowerCase(locale);
        }

        @Override
        public String toUpperCase() {
            return toString().toUpperCase();
        }

        @Override
        public String toUpperCase(Locale locale) {
            return toString().toUpperCase(locale);
        }

        @Override
        public String trim() {
            return toString().trim();
        }

        @Override
        public char[] toCharArray() {
            return toString().toCharArray();
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public byte[] getBytes(String charsetName) {
            assertUtf8(charsetName);
            return bytes;
        }

        @Override
        public byte[] getBytes(Charset charset) {
            assertUtf8(charset.name());
            return bytes;
        }

        @Override
        public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
            toString().getChars(srcBegin, srcEnd, dst, dstBegin);
        }

        @Override
        public boolean isEmpty() {
            return toString().isEmpty();
        }

        @Override
        public int codePointAt(int index) {
            return toString().codePointAt(index);
        }

        @Override
        public int codePointBefore(int index) {
            return toString().codePointBefore(index);
        }

        @Override
        public int codePointCount(int beginIndex, int endIndex) {
            return toString().codePointCount(beginIndex, endIndex);
        }

        @Override
        public int offsetByCodePoints(int index, int codePointOffset) {
            return toString().offsetByCodePoints(index, codePointOffset);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin) {
            toString().getBytes(srcBegin, srcEnd, dst, dstBegin);
        }

        @Override
        public boolean contentEquals(StringBuffer sb) {
            return toString().contentEquals(sb);
        }

        @Override
        public int indexOf(int ch, int beginIndex, int endIndex) {
            return toString().indexOf(ch, beginIndex, endIndex);
        }

        @Override
        public int indexOf(String str, int beginIndex, int endIndex) {
            return toString().indexOf(str, beginIndex, endIndex);
        }

        @Override
        public String replace(CharSequence target, CharSequence replacement) {
            return toString().replace(target, replacement);
        }

        @Override
        public String[] splitWithDelimiters(String regex, int limit) {
            return toString().splitWithDelimiters(regex, limit);
        }

        @Override
        public String strip() {
            return toString().strip();
        }

        @Override
        public String stripLeading() {
            return toString().stripLeading();
        }

        @Override
        public String stripTrailing() {
            return toString().stripTrailing();
        }

        @Override
        public boolean isBlank() {
            return toString().isBlank();
        }

        @Override
        public Stream<String> lines() {
            return toString().lines();
        }

        @Override
        public String indent(int n) {
            return toString().indent(n);
        }

        @Override
        public String stripIndent() {
            return toString().stripIndent();
        }

        @Override
        public String translateEscapes() {
            return toString().translateEscapes();
        }

        @Override
        public <R> R transform(Function<? super String, ? extends R> f) {
            return toString().transform(f);
        }

        @Override
        public IntStream chars() {
            return toString().chars();
        }

        @Override
        public IntStream codePoints() {
            return toString().codePoints();
        }

        @Override
        public String formatted(Object... args) {
            return toString().formatted(args);
        }

        @Override
        public String intern() {
            return toString().intern();
        }

        @Override
        public String repeat(int count) {
            return toString().repeat(count);
        }

        @Override
        public Optional<String> describeConstable() {
            return toString().describeConstable();
        }

        @Override
        public String resolveConstantDesc(MethodHandles.Lookup lookup) {
            return toString().resolveConstantDesc(lookup);
        }
    }

    private static final class Buffer extends ProtobufString {
        private final ByteBuffer buffer;
        private String decoded;
        private Buffer(ByteBuffer buffer) {
            if(!buffer.isReadOnly()) {
                throw new IllegalArgumentException("Only read only buffers are allowed");
            }

            this.buffer = buffer;
        }

        @Override
        public boolean equals(Object anObject) {
            return switch (anObject) {
                case Bytes lazyString -> equals(buffer, lazyString.bytes, lazyString.offset, lazyString.length);
                case Buffer lazyString -> buffer.mismatch(lazyString.buffer) == -1;
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
                if(decoded != null) {
                    return decoded;
                }

                return this.decoded = StandardCharsets.UTF_8.decode(buffer).toString();
            }
        }

        @Override
        public int hashCode() {
            var start = buffer.position();
            var result = 1;
            for (var i = 0; i < buffer.remaining(); i++) {
                result = 31 * result + buffer.get(start + i);
            }
            return result;
        }

        @Override
        public void write(int field, ProtobufOutputStream outputStream) {
            outputStream.writeBytes(field, buffer);
        }

        @Override
        public int encodedLength() {
            return buffer.remaining();
        }

        @Override
        public int length() {
            return toString().length();
        }

        @Override
        public char charAt(int index) {
            return toString().charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return toString().subSequence(start, end);
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return toString().contentEquals(cs);
        }

        @Override
        public boolean equalsIgnoreCase(String anotherString) {
            return toString().equalsIgnoreCase(anotherString);
        }

        @Override
        public boolean regionMatches(int toffset, String other, int ooffset, int len) {
            return toString().regionMatches(toffset, other, ooffset, len);
        }

        @Override
        public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
            return toString().regionMatches(ignoreCase, toffset, other, ooffset, len);
        }

        @Override
        public boolean startsWith(String prefix, int toffset) {
            return toString().startsWith(prefix, toffset);
        }

        @Override
        public boolean startsWith(String prefix) {
            return toString().startsWith(prefix);
        }

        @Override
        public boolean endsWith(String suffix) {
            return toString().endsWith(suffix);
        }

        @Override
        public int indexOf(int ch) {
            return toString().indexOf(ch);
        }

        @Override
        public int indexOf(int ch, int fromIndex) {
            return toString().indexOf(ch, fromIndex);
        }

        @Override
        public int lastIndexOf(int ch) {
            return toString().lastIndexOf(ch);
        }

        @Override
        public int lastIndexOf(int ch, int fromIndex) {
            return toString().lastIndexOf(ch, fromIndex);
        }

        @Override
        public int indexOf(String str) {
            return toString().indexOf(str);
        }

        @Override
        public int indexOf(String str, int fromIndex) {
            return toString().indexOf(str, fromIndex);
        }

        @Override
        public int lastIndexOf(String str) {
            return toString().lastIndexOf(str);
        }

        @Override
        public int lastIndexOf(String str, int fromIndex) {
            return toString().lastIndexOf(str, fromIndex);
        }

        @Override
        public String substring(int beginIndex) {
            return toString().substring(beginIndex);
        }

        @Override
        public String substring(int beginIndex, int endIndex) {
            return toString().substring(beginIndex, endIndex);
        }

        @Override
        public String concat(String str) {
            return toString().concat(str);
        }

        @Override
        public String replace(char oldChar, char newChar) {
            return toString().replace(oldChar, newChar);
        }

        @Override
        public boolean matches(String regex) {
            return toString().matches(regex);
        }

        @Override
        public boolean contains(CharSequence s) {
            return toString().contains(s);
        }

        @Override
        public String replaceFirst(String regex, String replacement) {
            return toString().replaceFirst(regex, replacement);
        }

        @Override
        public String replaceAll(String regex, String replacement) {
            return toString().replaceAll(regex, replacement);
        }

        @Override
        public String[] split(String regex, int limit) {
            return toString().split(regex, limit);
        }

        @Override
        public String[] split(String regex) {
            return toString().split(regex);
        }

        @Override
        public String toLowerCase() {
            return toString().toLowerCase();
        }

        @Override
        public String toLowerCase(Locale locale) {
            return toString().toLowerCase(locale);
        }

        @Override
        public String toUpperCase() {
            return toString().toUpperCase();
        }

        @Override
        public String toUpperCase(Locale locale) {
            return toString().toUpperCase(locale);
        }

        @Override
        public String trim() {
            return toString().trim();
        }

        @Override
        public char[] toCharArray() {
            return toString().toCharArray();
        }

        @Override
        public byte[] getBytes() {
            var result = new byte[buffer.remaining()];
            buffer.get(result);
            return result;
        }

        @Override
        public byte[] getBytes(String charsetName) {
            assertUtf8(charsetName);
            return getBytes();
        }

        @Override
        public byte[] getBytes(Charset charset) {
            assertUtf8(charset.name());
            return getBytes();
        }

        @Override
        public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
            toString().getChars(srcBegin, srcEnd, dst, dstBegin);
        }

        @Override
        public boolean isEmpty() {
            return toString().isEmpty();
        }

        @Override
        public int codePointAt(int index) {
            return toString().codePointAt(index);
        }

        @Override
        public int codePointBefore(int index) {
            return toString().codePointBefore(index);
        }

        @Override
        public int codePointCount(int beginIndex, int endIndex) {
            return toString().codePointCount(beginIndex, endIndex);
        }

        @Override
        public int offsetByCodePoints(int index, int codePointOffset) {
            return toString().offsetByCodePoints(index, codePointOffset);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin) {
            toString().getBytes(srcBegin, srcEnd, dst, dstBegin);
        }

        @Override
        public boolean contentEquals(StringBuffer sb) {
            return toString().contentEquals(sb);
        }

        @Override
        public int indexOf(int ch, int beginIndex, int endIndex) {
            return toString().indexOf(ch, beginIndex, endIndex);
        }

        @Override
        public int indexOf(String str, int beginIndex, int endIndex) {
            return toString().indexOf(str, beginIndex, endIndex);
        }

        @Override
        public String replace(CharSequence target, CharSequence replacement) {
            return toString().replace(target, replacement);
        }

        @Override
        public String[] splitWithDelimiters(String regex, int limit) {
            return toString().splitWithDelimiters(regex, limit);
        }

        @Override
        public String strip() {
            return toString().strip();
        }

        @Override
        public String stripLeading() {
            return toString().stripLeading();
        }

        @Override
        public String stripTrailing() {
            return toString().stripTrailing();
        }

        @Override
        public boolean isBlank() {
            return toString().isBlank();
        }

        @Override
        public Stream<String> lines() {
            return toString().lines();
        }

        @Override
        public String indent(int n) {
            return toString().indent(n);
        }

        @Override
        public String stripIndent() {
            return toString().stripIndent();
        }

        @Override
        public String translateEscapes() {
            return toString().translateEscapes();
        }

        @Override
        public <R> R transform(Function<? super String, ? extends R> f) {
            return toString().transform(f);
        }

        @Override
        public IntStream chars() {
            return toString().chars();
        }

        @Override
        public IntStream codePoints() {
            return toString().codePoints();
        }

        @Override
        public String formatted(Object... args) {
            return toString().formatted(args);
        }

        @Override
        public String intern() {
            return toString().intern();
        }

        @Override
        public String repeat(int count) {
            return toString().repeat(count);
        }

        @Override
        public Optional<String> describeConstable() {
            return toString().describeConstable();
        }

        @Override
        public String resolveConstantDesc(MethodHandles.Lookup lookup) {
            return toString().resolveConstantDesc(lookup);
        }
    }

    private static final class Value extends ProtobufString {
        private final String value;
        private WeakReference<byte[]> bytes;
        private Value(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object anObject) {
            return switch (anObject) {
                case Buffer lazyString -> Objects.equals(value, lazyString.toString());
                case Bytes lazyString -> Objects.equals(value, lazyString.toString());
                case Value wrappedString -> Objects.equals(value, wrappedString.value);
                case null, default -> false;
            };
        }

        @Override
        public void write(int field, ProtobufOutputStream outputStream) {
            outputStream.writeBytes(field, getBytes());
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(getBytes());
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

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return value.contentEquals(cs);
        }

        @Override
        public boolean equalsIgnoreCase(String anotherString) {
            return value.equalsIgnoreCase(anotherString);
        }

        @Override
        public boolean regionMatches(int toffset, String other, int ooffset, int len) {
            return value.regionMatches(toffset, other, ooffset, len);
        }

        @Override
        public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
            return value.regionMatches(ignoreCase, toffset, other, ooffset, len);
        }

        @Override
        public boolean startsWith(String prefix, int toffset) {
            return value.startsWith(prefix, toffset);
        }

        @Override
        public boolean startsWith(String prefix) {
            return value.startsWith(prefix);
        }

        @Override
        public boolean endsWith(String suffix) {
            return value.endsWith(suffix);
        }

        @Override
        public int indexOf(int ch) {
            return value.indexOf(ch);
        }

        @Override
        public int indexOf(int ch, int fromIndex) {
            return value.indexOf(ch, fromIndex);
        }

        @Override
        public int lastIndexOf(int ch) {
            return value.lastIndexOf(ch);
        }

        @Override
        public int lastIndexOf(int ch, int fromIndex) {
            return value.lastIndexOf(ch, fromIndex);
        }

        @Override
        public int indexOf(String str) {
            return value.indexOf(str);
        }

        @Override
        public int indexOf(String str, int fromIndex) {
            return value.indexOf(str, fromIndex);
        }

        @Override
        public int lastIndexOf(String str) {
            return value.lastIndexOf(str);
        }

        @Override
        public int lastIndexOf(String str, int fromIndex) {
            return value.lastIndexOf(str, fromIndex);
        }

        @Override
        public String substring(int beginIndex) {
            return value.substring(beginIndex);
        }

        @Override
        public String substring(int beginIndex, int endIndex) {
            return value.substring(beginIndex, endIndex);
        }

        @Override
        public String concat(String str) {
            return value.concat(str);
        }

        @Override
        public String replace(char oldChar, char newChar) {
            return value.replace(oldChar, newChar);
        }

        @Override
        public boolean matches(String regex) {
            return value.matches(regex);
        }

        @Override
        public boolean contains(CharSequence s) {
            return value.contains(s);
        }

        @Override
        public String replaceFirst(String regex, String replacement) {
            return value.replaceFirst(regex, replacement);
        }

        @Override
        public String replaceAll(String regex, String replacement) {
            return value.replaceAll(regex, replacement);
        }

        @Override
        public String[] split(String regex, int limit) {
            return value.split(regex, limit);
        }

        @Override
        public String[] split(String regex) {
            return value.split(regex);
        }

        @Override
        public String toLowerCase() {
            return value.toLowerCase();
        }

        @Override
        public String toLowerCase(Locale locale) {
            return value.toLowerCase(locale);
        }

        @Override
        public String toUpperCase() {
            return value.toUpperCase();
        }

        @Override
        public String toUpperCase(Locale locale) {
            return value.toUpperCase(locale);
        }

        @Override
        public String trim() {
            return value.trim();
        }

        @Override
        public char[] toCharArray() {
            return value.toCharArray();
        }

        @Override
        public byte[] getBytes() {
            if(bytes != null) {
                var result = bytes.get();
                if(result != null) {
                    return result;
                }
            }

            synchronized (this) {
                if(bytes != null) {
                    var result = bytes.get();
                    if(result != null) {
                        return result;
                    }
                }

                var result = value.getBytes(StandardCharsets.UTF_8);
                this.bytes = new WeakReference<>(result);
                return result;
            }
        }

        @Override
        public byte[] getBytes(String charsetName) {
            assertUtf8(charsetName);
            return getBytes();
        }

        @Override
        public byte[] getBytes(Charset charset) {
            assertUtf8(charset.name());
            return getBytes();
        }

        @Override
        public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
            value.getChars(srcBegin, srcEnd, dst, dstBegin);
        }

        @Override
        public boolean isEmpty() {
            return value.isEmpty();
        }

        @Override
        public int codePointAt(int index) {
            return value.codePointAt(index);
        }

        @Override
        public int codePointBefore(int index) {
            return value.codePointBefore(index);
        }

        @Override
        public int codePointCount(int beginIndex, int endIndex) {
            return value.codePointCount(beginIndex, endIndex);
        }

        @Override
        public int offsetByCodePoints(int index, int codePointOffset) {
            return value.offsetByCodePoints(index, codePointOffset);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin) {
            value.getBytes(srcBegin, srcEnd, dst, dstBegin);
        }

        @Override
        public boolean contentEquals(StringBuffer sb) {
            return value.contentEquals(sb);
        }

        @Override
        public int indexOf(int ch, int beginIndex, int endIndex) {
            return value.indexOf(ch, beginIndex, endIndex);
        }

        @Override
        public int indexOf(String str, int beginIndex, int endIndex) {
            return value.indexOf(str, beginIndex, endIndex);
        }

        @Override
        public String replace(CharSequence target, CharSequence replacement) {
            return value.replace(target, replacement);
        }

        @Override
        public String[] splitWithDelimiters(String regex, int limit) {
            return value.splitWithDelimiters(regex, limit);
        }

        @Override
        public String strip() {
            return value.strip();
        }

        @Override
        public String stripLeading() {
            return value.stripLeading();
        }

        @Override
        public String stripTrailing() {
            return value.stripTrailing();
        }

        @Override
        public boolean isBlank() {
            return value.isBlank();
        }

        @Override
        public Stream<String> lines() {
            return value.lines();
        }

        @Override
        public String indent(int n) {
            return value.indent(n);
        }

        @Override
        public String stripIndent() {
            return value.stripIndent();
        }

        @Override
        public String translateEscapes() {
            return value.translateEscapes();
        }

        @Override
        public <R> R transform(Function<? super String, ? extends R> f) {
            return value.transform(f);
        }

        @Override
        public IntStream chars() {
            return value.chars();
        }

        @Override
        public IntStream codePoints() {
            return value.codePoints();
        }

        @Override
        public String formatted(Object... args) {
            return value.formatted(args);
        }

        @Override
        public String intern() {
            return value.intern();
        }

        @Override
        public String repeat(int count) {
            return value.repeat(count);
        }

        @Override
        public Optional<String> describeConstable() {
            return value.describeConstable();
        }

        @Override
        public String resolveConstantDesc(MethodHandles.Lookup lookup) {
            return value.resolveConstantDesc(lookup);
        }
    }
}
