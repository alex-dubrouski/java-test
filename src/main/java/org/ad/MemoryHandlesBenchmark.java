package org.ad;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SequenceLayout;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 5)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-Xms8g", "-Xmx8g", "--add-modules", "jdk.incubator.foreign", "-XX:MaxDirectMemorySize=2G"})
@Threads(1)
public class MemoryHandlesBenchmark {
  static final byte[] bytes = new byte[1_073_741_824];
  static final ByteBuffer bufD = ByteBuffer.allocateDirect(1_073_741_824).order(ByteOrder.nativeOrder());
  static final ByteBuffer bufI = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
  static final SequenceLayout arrayLayout = MemoryLayout.ofSequence(134_217_728, MemoryLayout.ofValueBits(64, ByteOrder.nativeOrder()));
  static final VarHandle elemHandle = arrayLayout.varHandle(long.class, MemoryLayout.PathElement.sequenceElement());
  static final MemorySegment segment = MemorySegment.allocateNative(arrayLayout);
  static final MemoryAddress base = segment.baseAddress();
  int posI;
  long posL;

  @Setup(Level.Iteration)
  public void setup() {
    posI = ThreadLocalRandom.current().nextInt(100_000_000);
    posL = posI;
  }

  @Benchmark
  public void rwBBD(Blackhole bh) {
    for(int i = 0; i < 100_000; i++) {
      bufD.putLong((posI + i ) * 8, posI);
      bh.consume(bufD.getLong(posI * 8));
    }
  }

  @Benchmark
  public void rwBBI(Blackhole bh) {
    for(int i = 0; i < 100_000; i++) {
      bufI.putLong((posI + i ) * 8, posI);
      bh.consume(bufI.getLong(posI * 8));
    }
  }

  @Benchmark
  public void rwMH(Blackhole bh) {
    for (int i = 0; i < 100_000; i++) {
      elemHandle.set(base, posL + i, posL);
      //Explicit conversion pushes JVM to return unboxed type
      //Otherwise it will convert long -> Long (due to VarHandle polymorphic signature / bh.consume signature) and this benchmark will be 3 times slower
      bh.consume((long) elemHandle.get(base, posL));
    }
  }

  @TearDown
  public void teardown() {
    segment.close();
  }
}
