package org.ad;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
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
public class BoxUnboxBenchmark {
  int[] ints;
  int t0 = 0;

  @Setup
  public void setup() {
    ints = new Random(1).ints(1_000_000, 0, 1000).toArray();
  }

  /**
   * Testing boxing/unboxing primitive types
   * @param bh blackhole
   */
  @Benchmark
  public void testPrimitive(Blackhole bh) {
    for (int i = 0; i < ints.length; i++) {
      int t = ints[i];
      if (t % 2 == 0) {
        bh.consume(t);
      } else {
        bh.consume(t + t0);
      }
    }
  }

  @Benchmark
  public void testBoxedEscaping(Blackhole bh) {
    for (int i = 0; i < ints.length; i++) {
      Integer t = ints[i];  //t0 = 0 primitive int
      if (t % 2 == 0) {
        bh.consume(t);
      } else {
        bh.consume(t + t0);
      }
    }
  }

  @Benchmark
  public void testBoxedNonEscaping(Blackhole bh) {
    for (int i = 0; i < ints.length; i++) {
      Integer t = ints[i];  //t0 = 0 primitive int
      if (t % 2 == 0) {
        bh.consume((int)t);
      } else {
        bh.consume(t + t0);
      }
    }
  }
}
