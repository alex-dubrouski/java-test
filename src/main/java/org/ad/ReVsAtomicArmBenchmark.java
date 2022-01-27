package org.ad;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
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
//Disabled LSE to check if LL/SC could be faster than ARM v8.1 SWP
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:+AlwaysPreTouch", "-Xms2g", "-Xmx2g", "-XX:-RestrictContended", "-XX:-UseLSE"})
@Threads(8)
public class ReVsAtomicArmBenchmark {
  @Contended
  private final AtomicInteger _atomicInteger = new AtomicInteger(0);
  @Contended
  private final AtomicLong _atomicLong = new AtomicLong(0L);
  @Contended
  private final LongAdder _longAdder = new LongAdder();
  @Contended
  private final ReentrantLock l = new ReentrantLock();
  @Contended
  private int number = 0;
  @Contended
  private final Object lock = new Object();

  @Benchmark
  public int testReentrantLock() {
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
  public int testAtomicInt() {
    for(int i = 0; i < 100; i++) {
      _atomicInteger.incrementAndGet();
    }
    return _atomicInteger.get();
  }

  @Benchmark
  public long testAtomicLong() {
    for(int i = 0; i < 100; i++) {
      _atomicLong.incrementAndGet();
    }
    return _atomicLong.get();
  }

  @Benchmark
  public long testLongAdder() {
    for(int i = 0; i < 100; i++) {
      _longAdder.add(1L);
    }
    return _longAdder.sum();
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
