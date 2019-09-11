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
Optional test can not survive no-op GC 

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
`private static synthetic` static method)
Thanks @jguerra for help with Lambda benchmarks