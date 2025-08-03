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
        var length = new String(SHORT_UTF8_BYTES, StandardCharsets.UTF_8).length();
        for(var i = 0; i < length; i++) {
            new String(SHORT_UTF8_BYTES, StandardCharsets.UTF_8).charAt(i);
        }
    }
    
    @Benchmark
    public void shortLazyCharAt() {
        var length = ProtobufString.lazy(SHORT_UTF8_BYTES).length();
        for(var i = 0; i < length; i++) {
            ProtobufString.lazy(SHORT_UTF8_BYTES).charAt(i);
        }
    }
    
    @Benchmark
    public void mediumStringCharAt() {
        var length = new String(MEDIUM_UTF8_BYTES, StandardCharsets.UTF_8).length();
        for(var i = 0; i < length; i++) {
            new String(MEDIUM_UTF8_BYTES, StandardCharsets.UTF_8).charAt(i);
        }
    }
    
    @Benchmark
    public void mediumLazyCharAt() {
        var length = ProtobufString.lazy(MEDIUM_UTF8_BYTES).length();
        for(var i = 0; i < length; i++) {
            ProtobufString.lazy(MEDIUM_UTF8_BYTES).charAt(i);
        }
    }
    
    @Benchmark
    public void longStringCharAt() {
        var length = new String(LONG_UTF8_BYTES, StandardCharsets.UTF_8).length();
        for(var i = 0; i < length; i++) {
            new String(LONG_UTF8_BYTES, StandardCharsets.UTF_8).charAt(i);
        }
    }
    
    @Benchmark
    public void longLazyCharAt() {
        var length = ProtobufString.lazy(LONG_UTF8_BYTES).length();
        for(var i = 0; i < length; i++) {
            ProtobufString.lazy(LONG_UTF8_BYTES).charAt(i);
        }
    }
    
    @Benchmark
    public void shortStringSubSequence() {
        var length = new String(SHORT_UTF8_BYTES, StandardCharsets.UTF_8).length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                new String(SHORT_UTF8_BYTES, StandardCharsets.UTF_8).subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void shortLazySubSequence() {
        var length = ProtobufString.lazy(SHORT_UTF8_BYTES).length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                ProtobufString.lazy(SHORT_UTF8_BYTES).subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void mediumStringSubSequence() {
        var length = new String(MEDIUM_UTF8_BYTES, StandardCharsets.UTF_8).length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                new String(MEDIUM_UTF8_BYTES, StandardCharsets.UTF_8).subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void mediumLazySubSequence() {
        var length = ProtobufString.lazy(MEDIUM_UTF8_BYTES).length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                ProtobufString.lazy(MEDIUM_UTF8_BYTES).subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void longStringSubSequence() {
        var length = new String(LONG_UTF8_BYTES, StandardCharsets.UTF_8).length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                new String(LONG_UTF8_BYTES, StandardCharsets.UTF_8).subSequence(i, j);
            }
        }
    }
    
    @Benchmark
    public void longLazySubSequence() {
        var length = ProtobufString.lazy(LONG_UTF8_BYTES).length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                ProtobufString.lazy(LONG_UTF8_BYTES).subSequence(i, j);
            }
        }
    }
}