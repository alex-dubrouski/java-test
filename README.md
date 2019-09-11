# java-test

## Idea
Recent Java releases added a lot of syntactical sugarcoating like `Collection.stream().forEach()` and in this repo
I would like to use OpenJDK JMH benchmarking framework to find out how it affects performance.

## Running

- To build code `$ mvn clean install`
- To run it `$ java -jar target/benchmarks.jar`
- To see list of possible options `java -jar target/benchmarks.jar -h`

## Prerequisites

Running this benchmark requires Java 12 as I used EpsilonGC to avoid possible GC overhead (ShenandoahGC for some tests, please visit https://adoptopenjdk.net to download JDK12 with ShenandoahGC)
These tests were executed with AdoptOpenJDK `JDK 12.0.2, OpenJDK 64-Bit Server VM, 12.0.2+10`, hsdis compiled from OpenJDK source (binutils 2.32), 

## Test results
I ran these tests on idle development server [2x AMD Opteron(tm) Processor 6328, 256GB]
### Notes
- Optional creates so much memory overhead that I had to rollback from Epsilon no-op to Shenandoah compacting GC to avoid OOMs [Heap is 4GB, ArrayList requires (1MM * 20bytes + some overhead) and Optional benchmark fail at 4 iteration, so ~4MM of Optionals have approximate size of 3.8GB]
- All other tests except `if vs Optional` are running no-op EpsilonGC to exclude GC overhead
- Bigger numbers mean worse result as metric is microseconds per operation (us/op)
- Size is the size of ArrayList used for benchmark (it pre-filled with `String$i` strings, where i is [0..size] to make sure GC won't do deduplication)
- Tests do not produce assembly code by default, but I captured hot spots with help of `-prof perfasm` and added text files with assembly code, you can find it `docs` directory

### Conventional If versus Optional.ofNullable().ifPresent() vs stream().filter(Objects::nonNull).forEach() [Bigger is worse]
```
Benchmark                              (size)  Mode  Cnt       Score        Error  Units
IfBenchmark.walk                       100000  avgt   50     590.587 ±      5.900  us/op
IfBenchmark.walk                      1000000  avgt   50    5779.019 ±     36.016  us/op
OptionalBenchmark.walk                 100000  avgt   50    1156.084 ±     14.082  us/op
OptionalBenchmark.walk                1000000  avgt   50   15887.035 ±    320.294  us/op
StreamWithFilterBenchmark.walk         100000  avgt   50    2644.813 ±    134.190  us/op
StreamWithFilterBenchmark.walk        1000000  avgt   50   32373.471 ±   1851.291  us/op
```
This group of tests is using `-XX:+UseShenandoahGC -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:-UseBiasedLocking` because
Optional test can not survive no-op GC. Stream based test has different hot spots for 100K and 1MM collections
Profiling GC shows astonishing difference:
```
# JMH version: 1.21
# VM version: JDK 12.0.2, OpenJDK 64-Bit Server VM, 12.0.2+10
# VM invoker: /home/adubrouski/jdk12/bin/java
# VM options: -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:-UseBiasedLocking -Xms2g -Xmx2g
# Warmup: 3 iterations, 1 s each
# Measurement: 50 iterations, 1 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Average time, time/op
# Benchmark: org.ad.IfBenchmark.walk
# Parameters: (size = 100000)

# Run progress: 0.00% complete, ETA 00:01:46
# Fork: 1 of 1
# Warmup Iteration   1: 578.569 us/op
# Warmup Iteration   2: 575.984 us/op
# Warmup Iteration   3: 565.938 us/op
Iteration   1: 562.636 us/op
                 ·gc.alloc.rate:      ≈ 10⁻³ MB/sec
                 ·gc.alloc.rate.norm: 0.288 B/op
                 ·gc.count:           ≈ 0 counts

Iteration   2: 563.880 us/op
                 ·gc.alloc.rate:      ≈ 10⁻³ MB/sec
                 ·gc.alloc.rate.norm: 0.289 B/op
                 ·gc.count:           ≈ 0 counts
```
so there is literally no allocation, while for Optional code picture is completely different
```
# JMH version: 1.21
# VM version: JDK 12.0.2, OpenJDK 64-Bit Server VM, 12.0.2+10
# VM invoker: /home/adubrouski/jdk12/bin/java
# VM options: -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:-UseBiasedLocking -Xms2g -Xmx2g
# Warmup: 3 iterations, 1 s each
# Measurement: 50 iterations, 1 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Average time, time/op
# Benchmark: org.ad.OptionalBenchmark.walk
# Parameters: (size = 100000)

# Run progress: 0.00% complete, ETA 00:01:46
# Fork: 1 of 1
# Warmup Iteration   1: 1187.868 us/op
# Warmup Iteration   2: 1195.507 us/op
# Warmup Iteration   3: 1148.178 us/op
Iteration   1: 1296.817 us/op
                 ·gc.alloc.rate:      588.064 MB/sec
                 ·gc.alloc.rate.norm: 1200000.715 B/op
                 ·gc.count:           ≈ 0 counts

Iteration   2: 1307.376 us/op
                 ·gc.alloc.rate:            583.337 MB/sec
                 ·gc.alloc.rate.norm:       1200000.668 B/op
                 ·gc.churn.Shenandoah:      1133.834 MB/sec
                 ·gc.churn.Shenandoah.norm: 2332447.238 B/op
                 ·gc.count:                 3.000 counts
                 ·gc.time:                  6.000 ms

Iteration   3: 1255.745 us/op
                 ·gc.alloc.rate:      607.271 MB/sec
                 ·gc.alloc.rate.norm: 1200000.642 B/op
                 ·gc.count:           ≈ 0 counts
```
I used EpsilonGC with `-XX:+HeapDumpOnOutOfMemoryError` to catch the dump and analyze it and result is pretty strange. 
Test fails during 3rd iteration with 100K ArrayList
```
# JMH version: 1.21
# VM version: JDK 12.0.2, OpenJDK 64-Bit Server VM, 12.0.2+10
# VM invoker: /home/adubrouski/jdk12/bin/java
# VM options: -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:+AlwaysPreTouch -Xms2g -Xmx2g -XX:+HeapDumpOnOutOfMemoryError
# Warmup: 3 iterations, 1 s each
# Measurement: 50 iterations, 1 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Average time, time/op
# Benchmark: org.ad.OptionalBenchmark.walk
# Parameters: (size = 100000)

# Run progress: 0.00% complete, ETA 00:01:46
# Fork: 1 of 1
# Warmup Iteration   1: 844.715 us/op
# Warmup Iteration   2: 827.103 us/op
# Warmup Iteration   3: java.lang.OutOfMemoryError: Java heap space
Dumping heap to java_pid26975.hprof ...
Heap dump file created [4418738360 bytes in 24.625 secs]
Terminating due to java.lang.OutOfMemoryError: Java heap space
```
Examining heap shows that there are 133MM of Optional objects, which are holding 50K of Strings (ArrayList is filled with 50/50 Strings and nulls)
#### [Heap Content](images/HeapContent.png)
#### [Optional Objects referencing Strings](images/OptionalReferencesStrings.png)

### Simple for loop versus stream().forEach() vs ArrayList.forEach() [Bigger is worse]
```
Benchmark                              (size)  Mode  Cnt       Score        Error  Units
CollectionForEachBenchmark.walk        100000  avgt   50    2064.416 ±     26.381  us/op
CollectionForEachBenchmark.walk       1000000  avgt   50   48509.926 ±   2938.986  us/op
LoopBenchmark.walk                     100000  avgt   50    1203.630 ±     42.822  us/op
LoopBenchmark.walk                    1000000  avgt   50   11095.643 ±    510.536  us/op
StreamForEachBenchmark.walk            100000  avgt   50    2054.006 ±    120.990  us/op
StreamForEachBenchmark.walk           1000000  avgt   50   64965.610 ±   5365.483  us/op
```
`Collection.forEach` and `Collection.stream().forEach()` have similar performance

### stream().parallel().forEach() versus submitting parallel tasks to custom ForkJoinPool [Bigger is worse]
```
Benchmark                              (size)  Mode  Cnt       Score        Error  Units
StreamParallelBenchmark.walk               10  avgt    5   31829.706 ±    588.942  us/op
StreamParallelBenchmark.walk              200  avgt    5  673440.250 ± 137475.485  us/op
StreamParallelCustomTPBenchmark.walk       10  avgt    5   89570.131 ±   9826.232  us/op
StreamParallelCustomTPBenchmark.walk      200  avgt    5   84106.558 ±   2396.451  us/op
```
this test supposedly exhausts default FJPool which backs `stream().parallel()`. 
Also you might notice that for small collection custom approach is slower. This can be explained by the fact that 
there is an overhead to spawn custom FJPool and new threads, while default one starts with JVM.

### Anonymous classes vs Capturing and non-capturing lambdas and method references
```
Benchmark                              (size)  Mode  Cnt       Score        Error  Units
AnonymousInterfaceBenchmark.walk       100000  avgt   50    1851.074 ±     12.511  us/op
AnonymousInterfaceBenchmark.walk      1000000  avgt   50   48658.413 ±   2919.706  us/op
LambdaBenchmark.walkCapturingLambda    100000  avgt   50    1787.172 ±     46.124  us/op
LambdaBenchmark.walkCapturingLambda   1000000  avgt   50   45310.784 ±   2070.665  us/op
LambdaBenchmark.walkInlineLambda       100000  avgt   50    1879.110 ±     18.840  us/op
LambdaBenchmark.walkInlineLambda      1000000  avgt   50   46676.991 ±   2397.409  us/op
LambdaBenchmark.walkMethodReference    100000  avgt   50    1889.369 ±     19.933  us/op
LambdaBenchmark.walkMethodReference   1000000  avgt   50   47084.614 ±   2990.737  us/op
```
Overall there is no difference as compiler optimizes this code (inline lambda into a method, capturing lambda into
`private static synthetic` static method). 
Thanks @jguerra for help with Lambda benchmarks