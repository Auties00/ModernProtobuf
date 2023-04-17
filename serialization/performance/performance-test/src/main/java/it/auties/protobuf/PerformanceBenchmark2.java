package it.auties.protobuf;

import org.openjdk.jmh.annotations.*;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 1, timeUnit = TimeUnit.MICROSECONDS)
public class PerformanceBenchmark2 {
    private static final Holder holder = new Holder("Hello World");
    private static final Supplier<String> runnable = holder::getTest;

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void direct() {
        var result = holder.getTest();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void lambda() {
        var result = runnable.get();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void reflection() throws Throwable {
        var result = (String) MethodHandles.publicLookup()
                .findGetter(Holder.class, "test", String.class)
                .invokeExact(holder);
    }


    public static class Holder {
        private final String test;
        private Holder(String test) {
            this.test = test;
        }

        public String getTest() {
            return test;
        }
    }
}