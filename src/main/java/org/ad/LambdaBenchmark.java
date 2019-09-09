package org.ad;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-Xms2g", "-Xmx2g"})
@Threads(Threads.MAX)
public class LambdaBenchmark {
    @Param({"100000", "1000000"})
    int size;

    List<String> lst;
    Consumer consumer;
    Consumer2 consumer2;

    private void create() {
        lst = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lst.add("String" + i);
        }
        consumer = (bh, line) -> bh.consume(line);
        consumer2 = Blackhole::consume;
    }

    @Setup
    public void setup() throws Exception {
        create();
    }

    @Benchmark
    public void walkInlineLambda(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            consumer.consume(bh, lst.get(i));
        }
    }

    @Benchmark
    public void walkMethodReference(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            consumer2.consume(bh, lst.get(i));
        }
    }

    @Benchmark
    public void walkCapturingLambda(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            final String value = lst.get(i);
            java.util.function.Consumer<Blackhole> capturingConsumer = b -> b.consume(value);
            capturingConsumer.accept(bh);
        }
    }

    public interface Consumer {
        void consume(Blackhole bh, String line);
    }

    public interface Consumer2 {
        void consume(Blackhole bh, String line);
    }
}
