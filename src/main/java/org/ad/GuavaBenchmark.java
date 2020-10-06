package org.ad;

import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
import org.openjdk.jmh.infra.Blackhole;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:+DisableExplicitGC", "-XX:+UnlockDiagnosticVMOptions", "-XX:+AlwaysPreTouch", "-Xms24g", "-Xmx24g"})
@Threads(4)
public class GuavaBenchmark {

  private final static SecureRandom random = new SecureRandom();
  @Param({"1000"})
  int size;

  private List<Pojo> list;
  private List<Pojo> jImmutableList;
  private List<Pojo> gImmutableList;

  @Setup
  public void setup() {
    list = new ArrayList<>(size);
    for(int i = 0; i < size; i++) {
      list.add(new Pojo(ThreadLocalRandom.current().nextInt(),
          getAlphaNumericString(100),
          randomEnum(Letter.class)
      ));
    }
    jImmutableList = List.of(list.toArray(Pojo[]::new));
    gImmutableList = ImmutableList.copyOf(list);
  }

  /**
   * Testing different variations of immutable collections
   * @param bh blackhole
   */
  @Benchmark
  public void testImmutableList(Blackhole bh) {
    bh.consume(List.of(list.toArray(Pojo[]::new)));
  }

  @Benchmark
  public void testImmutableCollections(Blackhole bh) {
    bh.consume(Collections.unmodifiableList(list));
  }

  @Benchmark
  public void testImmutableGuava(Blackhole bh) {
    bh.consume(ImmutableList.copyOf(list));
  }

  @Benchmark
  public void testStream(Blackhole bh) {
    bh.consume(jImmutableList.stream().filter(x -> x.a > 0).flatMap(x -> IntStream.range(0,x.f.hashCode()).boxed()).filter(x -> x > 100).findFirst());
  }

  @Benchmark
  public void testStreamGuava(Blackhole bh) {
    bh.consume(gImmutableList.stream().filter(x -> x.a > 0).flatMap(x -> IntStream.range(0,x.f.hashCode()).boxed()).filter(x -> x > 100).findFirst());
  }

  static String getAlphaNumericString(int n) {

    byte[] array = new byte[256];
    new Random().nextBytes(array);

    String randomString
        = new String(array, StandardCharsets.UTF_8);

    StringBuilder r = new StringBuilder();

    for (int k = 0; k < randomString.length(); k++) {

      char ch = randomString.charAt(k);

      if (((ch >= 'a' && ch <= 'z')
          || (ch >= 'A' && ch <= 'Z')
          || (ch >= '0' && ch <= '9'))
          && (n > 0)) {

        r.append(ch);
        n--;
      }
    }
    return r.toString();
  }

  public static <T extends Enum<?>> T randomEnum(Class<T> clazz){
    int x = random.nextInt(clazz.getEnumConstants().length);
    return clazz.getEnumConstants()[x];
  }

  private class Pojo {
    int a;
    String b;
    Letter f;
    public Pojo(int a, String b, Letter f) {
      this.a = a;
      this.b = b;
      this.f = f;
    }
  }

  private enum Letter {
    a1,b1,c1,d1,e1,f1,g1,h1,i1,j1,k1,l1,m1,n1,o1,p1,r1,q1,s1,t1,u1,v1,w1,x1,y1,z1;
  }
}
