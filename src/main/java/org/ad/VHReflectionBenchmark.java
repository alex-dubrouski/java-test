package org.ad;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;
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


@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 25, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-Xms8g", "-Xmx8g"})
@Threads(1)
public class VHReflectionBenchmark {
  static final MethodHandle mh;
  static final VarHandle vh;
  static {
    try {
      mh = MethodHandles.lookup().findConstructor(MyClass.class, MethodType.methodType(void.class));
      vh = MethodHandles.privateLookupIn(MyClass.class, MethodHandles.lookup()).findVarHandle(MyClass.class, "name", String.class);
    } catch (IllegalAccessException | NoSuchMethodException | NoSuchFieldException var1) {
      throw new AssertionError(var1);
    }
  }

  @Benchmark
  public void testVH() throws Throwable {
    MyClass t = (MyClass) mh.invokeExact();
    vh.set(t, "done");
//    System.out.println(vh.get(t));
  }

  @Benchmark
  public void testReflection()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException,
             NoSuchFieldException {
    Constructor constructor = MyClass.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    MyClass t = (MyClass) constructor.newInstance();
    Field f = t.getClass().getDeclaredField("name");
    f.setAccessible(true);
    f.set(t, "done");
//    System.out.println(f.get(t));
  }
}


class MyClass {
  protected int id = 1;
  private String name = "test";
}
