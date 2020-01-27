package org.ad;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This is obvious, added to explain obvious things during presentation intro
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms4g", "-Xmx4g"})
@Threads(1)
public class ArrayLinkedListBenchmark {
    @Param({"100000"})
    int size;

    List<String> gAlist;
    List<String> gLlist;

    private void create() {
        gAlist = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            gAlist.add("String" + i);
        }
        gLlist = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            gLlist.add("String" + i);
        }
    }

    @Setup
    public void setup() throws Exception {
        create();
    }

    @Benchmark
    public void arrayListAdd(Blackhole bh) {
        List<String> alst = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            alst.add("String" + i);
        }
        bh.consume(alst);
    }

    @Benchmark
    public void linkedListAdd(Blackhole bh) {
        List<String> llst = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            llst.add("String" + i);
        }
        bh.consume(llst);
    }

    @Benchmark
    public void traverseAListForEach(Blackhole bh) {
        for (String s : gAlist) {
            bh.consume(s);
        }
    }

    @Benchmark
    public void traverseLListForEach(Blackhole bh) {
        for (String s : gAlist) {
            bh.consume(s);
        }
    }

    @Benchmark
    public void traverseAList(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(gAlist.get(i));
        }
    }

    @Benchmark
    public void traverseLList(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(gLlist.get(i));
        }
    }
}
