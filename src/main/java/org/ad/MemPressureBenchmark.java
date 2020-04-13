package org.ad;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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


@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-Xms8g", "-Xmx8g"})
@Threads(1)
public class MemPressureBenchmark {
  @Param({"1000000", "2000000", "3000000", "4000000", "5000000"})
  int size;

  public volatile int tlrMask;
  public int tlr;
  public volatile Object obj1;

  @Setup
  public void setup() throws Exception {
    Random r = new Random(System.nanoTime());
    tlr = r.nextInt();
    tlrMask = 1;
    obj1 = new Object();
  }

  @Benchmark
  public void walk() {
    List<String> lst = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      if (i % 2 == 0) {
        lst.add("String");
      } else {
        lst.add(null);
      }
    }
    for (int i = 0; i < size; i++) {
      Optional.ofNullable(lst.get(i)).ifPresent(this::consume);
    }
  }
  //Copy-paste of BlackHole consume method
  public final void consume(Object obj) {
    int tlrMask = this.tlrMask; // volatile read
    int tlr = (this.tlr = (this.tlr * 1664525 + 1013904223));
    if ((tlr & tlrMask) == 0) {
      // SHOULD ALMOST NEVER HAPPEN IN MEASUREMENT
      this.obj1 = new WeakReference<>(obj);
      this.tlrMask = (tlrMask << 1) + 1;
    }
  }
}
