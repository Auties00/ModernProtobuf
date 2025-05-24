package it.auties.proto.benchmark;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import it.auties.proto.benchmark.model.LiteScalar;
import it.auties.proto.benchmark.model.ModernScalarMessage;
import it.auties.proto.benchmark.model.ModernScalarMessageSpec;
import it.auties.proto.benchmark.model.Scalar;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class PrimitivesBenchmark {
    private static final int CAPACITY = 1000;
    private static final List<Scalar.ScalarMessage> GOOGLE_MESSAGES;
    private static final List<LiteScalar.ScalarMessage> LITE_GOOGLE_MESSAGES;
    private static final List<ModernScalarMessage> MODERN_MESSAGES;
    private static final List<byte[]> INPUTS;

    static {
        try {
            var random = new SecureRandom();
            var inputs = new ArrayList<byte[]>(CAPACITY);
            var googleMessages = new ArrayList<Scalar.ScalarMessage>(CAPACITY);
            var liteGoogleMessages = new ArrayList<LiteScalar.ScalarMessage>(CAPACITY);
            var modernMessages = new ArrayList<ModernScalarMessage>(CAPACITY);
            for(var i = 0; i < CAPACITY; i++) {
                var googleMessage = Scalar.ScalarMessage.newBuilder()
                        .setString("Hello, this is an automated test")
                        .setBytes(ByteString.copyFrom("Hello, this is an automated test".getBytes(StandardCharsets.UTF_8)))
                        .setFixed32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        .setSfixed32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        .setInt32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        .setUint32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        .setFixed64(random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
                        .setSfixed64(random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
                        .setInt64(random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
                        .setUint64(random.nextLong(0, Long.MAX_VALUE))
                        .setBool(random.nextBoolean())
                        .setDouble(random.nextDouble(Double.MIN_VALUE, Double.MAX_VALUE))
                        .setFloat(random.nextFloat(Float.MIN_VALUE, Float.MAX_VALUE))
                        .build();
                var serialized = googleMessage.toByteArray();
                googleMessages.add(googleMessage);
                liteGoogleMessages.add(LiteScalar.ScalarMessage.parseFrom(serialized));
                modernMessages.add(ModernScalarMessageSpec.decode(serialized));
                inputs.add(googleMessage.toByteArray());
            }
            GOOGLE_MESSAGES = googleMessages;
            LITE_GOOGLE_MESSAGES = liteGoogleMessages;
            MODERN_MESSAGES = modernMessages;
            INPUTS = inputs;
        } catch (Throwable throwable) {
            throw new RuntimeException("Cannot initialize benchmark", throwable);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void modernProtobufSerialization() {
        for(var input : MODERN_MESSAGES) {
            ModernScalarMessageSpec.encode(input);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void modernProtobufDeserialization() {
        for(var input : INPUTS) {
            ModernScalarMessageSpec.decode(input);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void googleProtobufSerialization() {
        for(var input : GOOGLE_MESSAGES) {
            input.toByteArray();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void googleProtobufDeserialization() throws InvalidProtocolBufferException {
        for(var input : INPUTS) {
            Scalar.ScalarMessage.parseFrom(input);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void googleLiteProtobufSerialization() {
        for(var input : LITE_GOOGLE_MESSAGES) {
            input.toByteArray();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void googleLiteProtobufDeserialization() throws InvalidProtocolBufferException {
        for(var input : INPUTS) {
            LiteScalar.ScalarMessage.parseFrom(input);
        }
    }
}