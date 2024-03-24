package org.ad;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 20, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseParallelGC", "-XX:+AlwaysPreTouch", "-Xms4g", "-Xmx4g"})
@Threads(1)
public class LoopsBenchmark {
    private static final int size = 1024;
    private static final List<Integer> list = new ArrayList<>(size);
    private static final int[] arr = new int[size];
    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_512;
    private static final int[] zeroes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    @Setup
    public void setup() {
        for (int i = 0; i < size; i++) {
            int k = ThreadLocalRandom.current().nextInt(1_000_000_000);
            list.add(k);
            arr[i] = k;
        }
    }


    @Benchmark
    public int bIntFor() {
        int r = 0;
        for (int i = 0; i < size; i++) {
            r += list.get(i);
        }
        return r;
    }

    @Benchmark
    public int bIntForUnroll() {
        int r1 = 0;
        int r2 = 0;
        int r3 = 0;
        int r4 = 0;
        for (int i = 0; i < size; i = i + 4) {
            r1 += list.get(i);
            r2 += list.get(i + 1);
            r3 += list.get(i + 2);
            r4 += list.get(i + 3);
        }
        return r1 + r2 + r3 + r4;
    }

    @Benchmark
    public Integer bIntegerFor() {
        Integer r = 0;
        for (int i = 0; i < size; i++) {
            r += list.get(i);
        }
        return r;
    }


    @Benchmark
    public int bIntForEach() {
        int r = 0;
        for (Integer i : list) {
            r += i;
        }
        return r;
    }

    @Benchmark
    public int bIntForPrimArr() {
        int r = 0;
        for (int i = 0; i < size; i = i + 4) {
            r += arr[i];
        }
        return r;
    }

    @Benchmark
    public int bIntForUnrollPrimArr() {
        int r1 = 0;
        int r2 = 0;
        int r3 = 0;
        int r4 = 0;
        for (int i = 0; i < size; i = i + 4) {
            r1 += arr[i];
            r2 += arr[i + 1];
            r3 += arr[i + 2];
            r4 += arr[i + 3];
        }
        return r1 + r2 + r3 + r4;
    }

    @Benchmark
    public int bVector() {
        IntVector vec = null;
        int result = 0;
        for (int i = 0; i < size; i += 16) {
            vec = IntVector.fromArray(SPECIES, arr, i);
            result += vec.reduceLanes(VectorOperators.ADD);
        }
        return result;
    }

    @Benchmark
    public int bVector2() {
        IntVector vec0 = IntVector.fromArray(SPECIES, zeroes, 0);
        for (int i = 0; i < size; i += 16) {
            var vec = IntVector.fromArray(SPECIES, arr, i);
            vec0 = vec0.add(vec);
        }
        return vec0.reduceLanes(VectorOperators.ADD);
    }
}
