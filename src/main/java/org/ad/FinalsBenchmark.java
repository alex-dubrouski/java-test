package org.ad;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 10)
@Measurement(iterations = 5, time = 10)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseParallelGC", "-XX:+DisableExplicitGC", "-XX:+UnlockDiagnosticVMOptions", "-XX:+AlwaysPreTouch", "-Xms20g", "-Xmx20g", "--enable-preview"})
@Threads(1)
public class FinalsBenchmark {
  private final static SecureRandom random = new SecureRandom();
  List<Pojo> l;

  @Setup
  public void setup() {
    l = new ArrayList<>(800);
    for (int i = 0; i < 800; i++) {
      Pojo p = new Pojo(getAlphaNumericString(100), ThreadLocalRandom.current().nextDouble(), getAlphaNumericString(100));
      l.add(p);
    }
  }

  /**
   * Testing how final modifier on local variables affects performance
   * @param bh
   */
  @Benchmark
  public void testNonFinal(Blackhole bh) {
    Map<String, Pojo2> m = new HashMap<>(800);
    for (int i = 0; i < l.size(); i++) {
      Pojo p = l.get(i);
      String fn = p._featureName.toString();
      Pojo2 p2 = new Pojo2(p._string, p._double, fn);
      m.put(fn, p2);
    }
    bh.consume(m);
  }

  @Benchmark
  public void testFinal(Blackhole bh) {
    final Map<String, Pojo2> m = new HashMap<>(800);
    for (int i = 0; i < l.size(); i++) {
      final Pojo p = l.get(i);
      final String fn = p._featureName;
      final Pojo2 p2 = new Pojo2(p._string, p._double, fn);
      m.put(fn, p2);
    }
    bh.consume(m);
  }

  private class Pojo {
    String _string;
    double _double;
    String _featureName;
    Pojo(String s, double d, String i) {
      _string = s;
      _double = d;
      _featureName = i;
    }
  }

  private class Pojo2 {
    String _string;
    double _double;
    String _featureName;
    Pojo2(String s, double d, String i) {
      _string = s;
      _double = d;
      _featureName = i;
    }
    Pojo2(Pojo p) {
      _string = p._string;
      _double = p._double;
      _featureName = p._featureName.toString();
    }
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
}
