package org.ad;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
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
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-Xms2g", "-Xmx2g", "-XX:-UseBiasedLocking", "-XX:+UnlockDiagnosticVMOptions", "-XX:-DoEscapeAnalysis", "-XX:LoopUnrollLimit=1"})
@Threads(1)
@CompilerControl(CompilerControl.Mode.DONT_INLINE)
public class PatternMatchingBenchmark {
  @Param({"1000", "10000"})
  int size;

  Object[] objs ;

  @Setup
  public void setup() {
    objs = new Object[size];
    for(int i = 0; i < size; i++) {
      if (i % 2 == 0) {
        objs[i] = "String" + i;
      } else  {
        objs[i] = i;
      }
    }
  }

  @Benchmark
  public void testObjPattern(Blackhole bh) {
    for(int i = 0; i < size; i++) {
      if (objs[i] instanceof String s) {
        bh.consume(s);
      } else if (objs[i] instanceof Integer in) {
        bh.consume(in);
      }
    }
  }

  @Benchmark
  public void testObjCast(Blackhole bh) {
    Double t = 0D;
    for(int i = 0; i < size; i++) {
      if (objs[i] instanceof String) {
        bh.consume((String)objs[i]);
      } else if (objs[i] instanceof Integer) {
        bh.consume((Integer)objs[i]);
      }
    }
  }
}
