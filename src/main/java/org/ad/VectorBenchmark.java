package org.ad;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UnlockDiagnosticVMOptions",
    "-XX:+UseParallelGC", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-Xms8g", "-Xmx8g",
    "--add-modules", "jdk.incubator.vector", "-XX:MaxDirectMemorySize=2G"})
@Threads(1)
public class VectorBenchmark {
  private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;
  private static float[] a, b, c;
  @Param({"64000", "64000000"})
  private int size;

  @Setup(Level.Iteration)
  public void setup() {
    a = new float[size];
    b = new float[size];
    c = new float[size];
    for (int i = 0; i < size; i++) {
      a[i] = ThreadLocalRandom.current().nextFloat();
      b[i] = ThreadLocalRandom.current().nextFloat();
      c[i] = ThreadLocalRandom.current().nextFloat();
    }
  }

  @Benchmark
  public float[] scalarComputation() {
    for (int i = 0; i < a.length; i++) {
      c[i] = (a[i] * a[i] + b[i] * b[i]) * -1.0f;
    }
    return c;
  }

  @Benchmark
  public float[] vectorComputation() {
    for (int i = 0; i < a.length; i += SPECIES.length()) {
      VectorMask<Float> m = SPECIES.indexInRange(i, a.length);
      FloatVector va = FloatVector.fromArray(SPECIES, a, i, m);
      FloatVector vb = FloatVector.fromArray(SPECIES, b, i, m);
      FloatVector vc = va.mul(va)
          .add(vb.mul(vb))
          .neg();
      vc.intoArray(c, i, m);
    }
    return c;
  }
}
