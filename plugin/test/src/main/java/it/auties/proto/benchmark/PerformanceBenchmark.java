package it.auties.proto.benchmark;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class PerformanceBenchmark {
    private static final int ITERATIONS = 1_000;
    private static final byte[] SERIALIZED_INPUT;
    private static final ObjectReader JACKSON_READER;
    static {
        try {
            SERIALIZED_INPUT = Scalar.ScalarMessage.newBuilder()
                    .setString("Hello, this is an automated test")
                    .setBytes(ByteString.copyFrom("Hello, this is an automated test".getBytes(StandardCharsets.UTF_8)))
                    .setFixed32(Integer.MAX_VALUE)
                    .setSfixed32(Integer.MAX_VALUE)
                    .setInt32(Integer.MAX_VALUE)
                    .setUint32(Integer.MAX_VALUE)
                    .setFixed64(Long.MAX_VALUE)
                    .setSfixed64(Long.MAX_VALUE)
                    .setBool(false)
                    .setDouble(Double.MAX_VALUE)
                    .setFloat(Float.MAX_VALUE)
                    .build()
                    .toByteArray();
            var protoSource = ClassLoader.getSystemClassLoader().getResource("scalar.proto");
            Objects.requireNonNull(protoSource, "Missing scalar proto");
            var protoSchema = Files.readString(Path.of(protoSource.toURI()));
            var schema = ProtobufSchemaLoader.std.parse(protoSchema);
            JACKSON_READER = new ProtobufMapper()
                    .readerFor(JacksonScalarMessage.class)
                    .with(schema);
        } catch (Throwable throwable) {
            throw new RuntimeException("Cannot initialize benchmark", throwable);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void modernProtobufPerformance() {
        for (var i = 0; i < ITERATIONS; ++i) {
            ModernScalarMessageSpec.decode(SERIALIZED_INPUT);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void jacksonProtobuf() throws IOException {
        for (var i = 0; i < ITERATIONS; ++i) {
            JACKSON_READER.readValue(SERIALIZED_INPUT);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void googleProtobuf() throws InvalidProtocolBufferException {
        for (var i = 0; i < ITERATIONS; ++i) {
            Scalar.ScalarMessage.parseFrom(SERIALIZED_INPUT);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void googleLiteProtobuf() throws InvalidProtocolBufferException {
        for (var i = 0; i < ITERATIONS; ++i) {
            LiteScalar.ScalarMessage.parseFrom(SERIALIZED_INPUT);
        }
    }
}