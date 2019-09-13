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
- Optional creates so much memory overhead that I had to rollback from Epsilon no-op to Shenandoah compacting GC to avoid OOMs [Allocating Optional objects requires a lot of memory]
- All other tests except `Optional` are running no-op EpsilonGC to exclude GC overhead
- Bigger numbers mean worse result as metric is microseconds per operation (us/op)
- Size is the size of ArrayList used for benchmark (it pre-filled with `String$i` strings, where i is [0..size] to make sure GC won't do deduplication)
- Tests do not produce assembly code by default, but I captured hot spots with help of `-prof perfasm` and added text files with assembly code, you can find it `docs` directory

### Conventional If versus Optional.ofNullable().ifPresent() vs stream().filter(Objects::nonNull).forEach() [Bigger is worse]
```
Benchmark                        (size)  Mode  Cnt      Score      Error  Units
IfBenchmark.walk                 100000    ss   50    692.066 ±   30.960  us/op
IfBenchmark.walk                1000000    ss   50   6998.919 ±  231.874  us/op
OptionalBenchmark.walk           100000    ss   50   1961.494 ±  166.290  us/op
OptionalBenchmark.walk          1000000    ss   50  26905.626 ± 2501.974  us/op
StreamWithFilterBenchmark.walk   100000    ss   50   2227.230 ±   76.654  us/op
StreamWithFilterBenchmark.walk  1000000    ss   50  38899.323 ± 2323.732  us/op
```
Optional test is using `-XX:+UseShenandoahGC -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:-UseBiasedLocking` because
Optional test can not survive no-op GC. Stream based test has different hot spots for 100K and 1MM collections
Profiling GC, list with 1MM elements:
```
# JMH version: 1.21
# VM version: JDK 12.0.2, OpenJDK 64-Bit Server VM, 12.0.2+10
# VM invoker: /home/adubrouski/jdk12/bin/java
# VM options: -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:+AlwaysPreTouch -Xms2g -Xmx2g
# Warmup: 3 iterations, 1 us each
# Measurement: 50 iterations, 1 us each
# Timeout: 10 min per iteration
# Threads: 16 threads
# Benchmark mode: Single shot invocation time
# Benchmark: org.ad.IfBenchmark.walk
# Parameters: (size = 1000000)

# Run progress: 50.00% complete, ETA 00:00:29
# Fork: 1 of 1
# Warmup Iteration   1: 141073.163 ±(99.9%) 30091.424 us/op
# Warmup Iteration   2: 10274.664 ±(99.9%) 3228.422 us/op
# Warmup Iteration   3: 7933.534 ±(99.9%) 2074.353 us/op
Iteration   1: 7062.306 ±(99.9%) 1585.828 us/op
                 ·gc.alloc.rate:      0.015 MB/sec
                 ·gc.alloc.rate.norm: 508.000 B/op
                 ·gc.count:           ≈ 0 counts

Iteration   2: 7633.737 ±(99.9%) 2390.011 us/op
                 ·gc.alloc.rate:      0.015 MB/sec
                 ·gc.alloc.rate.norm: 514.000 B/op
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
# Threads: 16 threads
# Benchmark mode: Single shot invocation time
# Benchmark: org.ad.OptionalBenchmark.walk
# Parameters: (size = 1000000)

# Run progress: 50.00% complete, ETA 00:00:28
# Fork: 1 of 1
# Warmup Iteration   1: 664979.425 ±(99.9%) 91444.656 us/op
# Warmup Iteration   2: 86879.835 ±(99.9%) 23592.295 us/op
# Warmup Iteration   3: 32553.854 ±(99.9%) 7772.345 us/op
Iteration   1: 25274.961 ±(99.9%) 4044.993 us/op
                 ·gc.alloc.rate:      328.623 MB/sec
                 ·gc.alloc.rate.norm: 12000634.500 B/op
                 ·gc.count:           ≈ 0 counts

Iteration   2: 28211.536 ±(99.9%) 7414.660 us/op
                 ·gc.alloc.rate:            323.503 MB/sec
                 ·gc.alloc.rate.norm:       12000617.000 B/op
                 ·gc.churn.Shenandoah:      722.086 MB/sec
                 ·gc.churn.Shenandoah.norm: 26786370.000 B/op
                 ·gc.count:                 5.000 counts
                 ·gc.time:                  23.000 ms
```

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