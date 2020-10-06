package org.ad;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseParallelGC", "-XX:+DisableExplicitGC", "-XX:+UnlockDiagnosticVMOptions", "-XX:+AlwaysPreTouch", "-Xms4g", "-Xmx4g"})
@Threads(1)
public class CollectionSizeBenchmark {
  Integer[] ints;
  int t0 = 0;

  @Param({"16", "1000", "10000", "100000", "1000000"})
  int size;

  @Setup
  public void setup() {
    ints = new Random(1).ints(1_000_000, 0, 1000).boxed().toArray(Integer[]::new);
  }

  /**
   * Testing how auto resizing affects collection performance
   * @param bh blackhole
   */
  @Benchmark
  public void addToCollection(Blackhole bh) {
    List<Integer> list = new ArrayList<>(size);
    for(Integer i:ints) {
      list.add(i);
    }
    bh.consume(list);
  }
}
