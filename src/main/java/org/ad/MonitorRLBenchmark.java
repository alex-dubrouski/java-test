package org.ad;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Threads(8)
public class MonitorRLBenchmark {
  final Object mutex = new Object();
  final ReentrantLock lock = new ReentrantLock(true);

  @Benchmark
  @Group("monitorrtm")
  @GroupThreads(8)
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  @Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:-UseBiasedLocking", "-XX:+UnlockDiagnosticVMOptions", "-XX:-DoEscapeAnalysis", "-XX:+AlwaysPreTouch", "-XX:LoopUnrollLimit=1", "-XX:+UseRTMLocking", "-Xms2g", "-Xmx2g"})
  public void lockOnMonitorRTM(Blackhole bh) {
    for(int i = 0; i < 25; i++) {
      synchronized (mutex) {
//        System.out.println(mutex);
//        System.out.println(Thread.currentThread().getId());
        bh.consume(0x42);
      }
    }
  }

  @Benchmark
  @Group("monitor")
  @GroupThreads(8)
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  @Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:-UseBiasedLocking", "-XX:+UnlockDiagnosticVMOptions", "-XX:-DoEscapeAnalysis", "-XX:+AlwaysPreTouch", "-XX:LoopUnrollLimit=1", "-Xms2g", "-Xmx2g"})
  public void lockOnMonitor(Blackhole bh) {
    for(int i = 0; i < 25; i++) {
      synchronized (mutex) {
//        System.out.println(mutex);
//        System.out.println(Thread.currentThread().getId());
        bh.consume(0x42);
      }
    }
  }

  @Benchmark
  @Group("relock")
  @GroupThreads(8)
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  @Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:-UseBiasedLocking", "-XX:+UnlockDiagnosticVMOptions", "-XX:-DoEscapeAnalysis", "-XX:+AlwaysPreTouch", "-XX:LoopUnrollLimit=1", "-Xms2g", "-Xmx2g"})
  public void lockOnReLock(Blackhole bh) {
    for(int i = 0; i < 25; i++) {
      lock.lock();
      try {
//        System.out.println(lock);
//        System.out.println(Thread.currentThread().getId());
        bh.consume(0x42);
      } finally {
        lock.unlock();
      }
    }
  }
}
