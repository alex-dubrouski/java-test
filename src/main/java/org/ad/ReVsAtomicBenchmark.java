package org.ad;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import jdk.internal.vm.annotation.Contended;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:+AlwaysPreTouch", "-Xms2g", "-Xmx2g", "-XX:-RestrictContended"})
@Threads(Threads.MAX)
public class ReVsAtomicBenchmark {
  @Contended
  private final AtomicInteger ai = new AtomicInteger(0);
  @Contended
  private final ReentrantLock l = new ReentrantLock();
  @Contended
  private int number = 0;
  @Contended
  private final Object lock = new Object();

  @Benchmark
  public int testRE() {
    for(int i = 0; i < 100; i++) {
      l.lock();
      try {
        number += 1;
      } finally {
        l.unlock();
      }
    }
    return number;
  }

  @Benchmark
  public int testAtomic() {
    for(int i = 0; i < 100; i++) {
      ai.incrementAndGet();
    }
    return ai.get();
  }

  @Benchmark
  public int testSync() {
    for(int i = 0; i < 100; i++) {
      synchronized (lock) {
        number += 1;
      }
    }
    return number;
  }
}
