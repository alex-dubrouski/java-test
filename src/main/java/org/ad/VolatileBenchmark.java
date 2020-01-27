package org.ad;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC", "-XX:+AlwaysPreTouch", "-Xms1g", "-Xmx1g"})
@Threads(Threads.MAX)
public class VolatileBenchmark {
    @Param({"10000", "100000"})
    int size;
    int i;
    volatile int vi;

    @Benchmark
    public void setI() {
        for (int j = 0; j < size; j++) {
            i = j + 1;
        }
    }

    @Benchmark
    public void setVI() {
        for (int j = 0; j < size; j++) {
            vi = j + 1;
        }
    }

    @Benchmark
    public void getI(Blackhole bh) {
        for (int j = 0; j < size; j++) {
            bh.consume(i);
        }
    }

    @Benchmark
    public void getVI(Blackhole bh) {
        for (int j = 0; j < size; j++) {
            bh.consume(vi);
        }
    }
}
