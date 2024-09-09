package it.auties.proto.benchmark.simple;

import com.google.protobuf.ByteString;
import it.auties.proto.benchmark.model.LiteScalar;
import it.auties.proto.benchmark.model.ModernScalarMessage;
import it.auties.proto.benchmark.model.ModernScalarMessageSpec;
import it.auties.proto.benchmark.model.Scalar;
import org.openjdk.jmh.annotations.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class SimplePerformanceBenchmark {
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
                var data = new byte[ThreadLocalRandom.current().nextInt(1, 8192)];
                ThreadLocalRandom.current().nextBytes(data);
                var googleMessage = Scalar.ScalarMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(data))
                        .setString(new String(data))
                        .setFixed32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        .setSfixed32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        .setInt32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        .setUint32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        .setFixed64(random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
                        .setSfixed64(random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
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
    public void modernProtobufDeserialization() {
        for(var input : INPUTS) {
            ModernScalarMessageSpec.decode(input);
        }
    }
}