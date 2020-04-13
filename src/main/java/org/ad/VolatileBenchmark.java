package org.ad;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:+AlwaysPreTouch", "-Xms1g", "-Xmx1g"})
@Threads(Threads.MAX)
public class VolatileBenchmark {
    @Param({"10000", "100000"})
    int size;
    int i;
    volatile int vi;
    volatile TestClazz tc = new TestClazz();

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
    public void setVTCA() {
        for (int j = 0; j < size; j++) {
            tc.a = j + 1;
        }
    }

    @Benchmark
    public void setVTCB() {
        for (long j = 0; j < size; j++) {
            tc.b = j + 1;
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

    @Benchmark
    public void getVTC(Blackhole bh) {
        for (int j = 0; j < size; j++) {
            bh.consume(tc);
        }
    }

    private class TestClazz {
        int a;
        long b;
        TestClazz() {
            a = 10;
            b = 100L;
        }
    }
}
