package org.ad;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 50000, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1000, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-XX:-UseBiasedLocking", "-Xms4g", "-Xmx4g"})
@Threads(Threads.MAX)
public class StringBenchmark {
    @Benchmark
    public String testConcat() {
        String result = "One";
        for (int i = 0; i < 100; i++) {
            result += "Two";
        }
        return result;
    }

    @Benchmark
    public String testStringBuilder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Two");
        }
        return sb.toString();
    }

    @Benchmark
    public String testStringBuffer() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 100; i++) {
            sb.append("Two");
        }
        return sb.toString();
    }

    @Benchmark
    public String testDummyConcat() {
        return "One" + "Two" + "Three" + "Four" + "Five";
    }

    @Benchmark
    public String testDummySB() {
        return (new StringBuilder()).append("One").append("Two").append("Three").append("Four").append("Five").toString();
    }

    @Benchmark
    public String testIntConcat() {
        int x = 1234;
        return "" + x;
    }

    @Benchmark
    public String testIntToString() {
        int x = 1234;
        return Integer.toString(x);
    }

    @Benchmark
    public String testIntToString2() {
        int x = 1234;
        return String.valueOf(x);
    }
}
