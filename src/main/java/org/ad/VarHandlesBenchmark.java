package org.ad;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
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


/**
 * Created out of examples by Alex Shipilev https://shipilev.net/talks/jpoint-April2016-varhandles.pdf
 * All rights belong to author
 */

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
//Access to Unsafe and LoopUnrollLimit to have readable ASM code
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-Xms8g", "-Xmx8g", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", "-XX:LoopUnrollLimit=1"})
@Threads(1)
public class VarHandlesBenchmark {
  Unsafe unsafe;
  byte[] bytes;
  @Param({"1000000"})
  int size;
  static final VarHandle VH = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

  @Setup(Level.Iteration)
  public void setup() throws Exception {
    unsafe = Unsafe.getUnsafe();
    bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      int t = ThreadLocalRandom.current().nextInt(1,127);
      bytes[i] = (byte) t;
    }
  }

  @Benchmark
  public boolean array_byte() {
    for (byte b : bytes) {
      if (b < 0) return false;
    }
    return true;
  }

  @Benchmark
  public boolean byteBuffer_byte() {
    ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
    for (int i = 0; i < bytes.length; i++) {
      if (b.get(i) < 0) return false;
    }
    return true;
  }

  @Benchmark
  public boolean byteBuffer_long() {
    //Wrap byte array into ByteBuffer in native order
    ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
    int i;
    for (i = 0; i < bytes.length - 7; i += 8) {
      //Read chunks of byte array into long and use mask to verify that all bytes are neagative
      if ((b.getLong(i) & 0x8080808080808080L) != 0) return false;
    }
    for (; i < bytes.length; i++) {
      //Checking tail directly
      if (b.get(i) < 0) return false;
    }
    return true;
  }

  @Benchmark
  public boolean unsafe_long() {
    int i;
    for (i = 20; i < bytes.length - 7; i += 8) {
      //Same but using Unsafe
      if ((unsafe.getLong(bytes, i) & 0x8080808080808080L) != 0)
        return false;
    }
    for (; i < bytes.length; i++) {
      if (bytes[i] < 0) return false;
    }
    return true;
  }

  @Benchmark
  public boolean varHandles() {
    int li;
    for (li = 0; li < bytes.length / 8; li++) {
      //Same but using VarHandle
      if (((long) VH.get(bytes, li) & 0x8080808080808080L) != 0)
        return false;
    }
    for (int bi = li * 8; bi < bytes.length; bi++) {
      if (bytes[bi] < 0) return false;
    }
    return true;
  }
}
