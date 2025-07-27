package it.auties.protobuf;

import it.auties.protobuf.model.ProtobufString;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@SuppressWarnings("ResultOfMethodCallIgnored")
public class ProtobufStringBenchmark {
    private static final byte[] SHORT_UTF8_BYTES = "Hello World".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MEDIUM_UTF8_BYTES = "The quick brown fox jumps over the lazy dog. This is a medium length string with some special characters: áéíóú, åäö, ñç".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LONG_UTF8_BYTES = ("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. " +
            "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. " +
            "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. " +
            "Café résumé naïve piñata façade Zürich").getBytes(StandardCharsets.UTF_8);

    @Benchmark
    public int shortStringLength() {
        return new String(SHORT_UTF8_BYTES, StandardCharsets.UTF_8).length();
    }
    
    @Benchmark
    public int shortLazyLength() {
        return ProtobufString.lazy(SHORT_UTF8_BYTES).length();
    }
    
    @Benchmark
    public int mediumStringLength() {
        return new String(MEDIUM_UTF8_BYTES, StandardCharsets.UTF_8).length();
    }
    
    @Benchmark
    public int mediumLazyLength() {
        return ProtobufString.lazy(MEDIUM_UTF8_BYTES).length();
    }
    
    @Benchmark
    public int longStringLength() {
        return new String(LONG_UTF8_BYTES, StandardCharsets.UTF_8).length();
    }
    
    @Benchmark
    public int longLazyLength() {
        return ProtobufString.lazy(LONG_UTF8_BYTES).length();
    }
    
    @Benchmark
    public void shortStringCharAt() {
        var string = new String(SHORT_UTF8_BYTES, StandardCharsets.UTF_8);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            string.charAt(i);
        }
    }
    
    @Benchmark
    public void shortLazyCharAt() {
        var string = ProtobufString.lazy(SHORT_UTF8_BYTES);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            string.charAt(i);
        }
    }
    
    @Benchmark
    public void mediumStringCharAt() {
        var string = new String(MEDIUM_UTF8_BYTES, StandardCharsets.UTF_8);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            string.charAt(i);
        }
    }
    
    @Benchmark
    public void mediumLazyCharAt() {
        var string = ProtobufString.lazy(MEDIUM_UTF8_BYTES);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            string.charAt(i);
        }
    }
    
    @Benchmark
    public void longStringCharAt() {
        var string = new String(LONG_UTF8_BYTES, StandardCharsets.UTF_8);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            string.charAt(i);
        }
    }
    
    @Benchmark
    public void longLazyCharAt() {
        var string = ProtobufString.lazy(LONG_UTF8_BYTES);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            string.charAt(i);
        }
    }
    
    @Benchmark
    public void shortStringSubSequence() {
        var string = new String(SHORT_UTF8_BYTES, StandardCharsets.UTF_8);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                string.subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void shortLazySubSequence() {
        var string = ProtobufString.lazy(SHORT_UTF8_BYTES);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                string.subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void mediumStringSubSequence() {
        var string = new String(MEDIUM_UTF8_BYTES, StandardCharsets.UTF_8);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                string.subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void mediumLazySubSequence() {
        var string = ProtobufString.lazy(MEDIUM_UTF8_BYTES);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                string.subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void longStringSubSequence() {
        var string = new String(LONG_UTF8_BYTES, StandardCharsets.UTF_8);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                string.subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void longLazySubSequence() {
        var string = ProtobufString.lazy(LONG_UTF8_BYTES);
        var length = string.length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                string.subSequence(i, j);
            }
        }
    }
}