package org.ad;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
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
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:-UseBiasedLocking", "-XX:+AlwaysPreTouch", "-Xms2g", "-Xmx2g"})
@Threads(8)
public class MonitorRLBenchmark {
  final Object mutex = new Object();
  ReentrantLock lock;

  @Setup
  public void setup() throws Exception {
    lock = new ReentrantLock();
  }

  @Benchmark
  @Group("monitor")
  @GroupThreads(8)
  public void lockOnMonitor(Blackhole bh) {
    for(int i = 0; i < 25; i++) {
      synchronized (mutex) {
        bh.consume(ThreadLocalRandom.current().nextInt());
      }
    }
  }

  @Benchmark
  @Group("relock")
  @GroupThreads(8)
  public void lockOnReLock(Blackhole bh) {
    for(int i = 0; i < 25; i++) {
      lock.lock();
      bh.consume(ThreadLocalRandom.current().nextInt());
      lock.unlock();
    }
  }
}
