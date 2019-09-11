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
Benchmark                        (size)  Mode  Cnt     Score       Error  Units
IfBenchmark.walk                 100000  avgt   50    580.566 ±    4.838  us/op
IfBenchmark.walk                1000000  avgt   50   5803.784 ±   30.833  us/op
OptionalBenchmark.walk           100000  avgt   50   1165.590 ±   31.221  us/op
OptionalBenchmark.walk          1000000  avgt   50  14476.035 ±  170.855  us/op
StreamWithFilterBenchmark.walk   100000  avgt   50   2363.002 ±   30.007  us/op
StreamWithFilterBenchmark.walk  1000000  avgt   50  32650.584 ± 2084.522  us/op
```
This group of tests is using `-XX:+UseShenandoahGC -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:-UseBiasedLocking` because
Optional test can not survive no-op GC 

### Simple for loop versus stream().forEach() vs ArrayList.forEach() [Bigger is worse]
```
Benchmark                         (size)  Mode  Cnt      Score      Error  Units
CollectionForEachBenchmark.walk   100000  avgt   50   2088.561 ±   31.492  us/op
CollectionForEachBenchmark.walk  1000000  avgt   50  47517.317 ± 3877.215  us/op
LoopBenchmark.walk                100000  avgt   50   1206.834 ±   75.190  us/op
LoopBenchmark.walk               1000000  avgt   50   9317.702 ±  414.633  us/op
StreamForEachBenchmark.walk       100000  avgt   50   2040.535 ±   57.324  us/op
StreamForEachBenchmark.walk      1000000  avgt   50  50436.311 ± 3105.695  us/op
```
`Collection.forEach` and `Collection.stream().forEach()` have similar performance

### stream().parallel().forEach() versus submitting parallel tasks to custom ForkJoinPool [Bigger is worse]
```
Benchmark                             (size)  Mode  Cnt       Score        Error  Units
StreamParallelBenchmark.walk              10  avgt    5   31502.180 ±    824.874  us/op
StreamParallelBenchmark.walk             200  avgt    5  661704.870 ± 107801.757  us/op
StreamParallelCustomTPBenchmark.walk      10  avgt    5   87797.423 ±   9514.741  us/op
StreamParallelCustomTPBenchmark.walk     200  avgt    5   83977.889 ±   2185.751  us/op
```
this test supposedly exhausts default FJPool which backs `stream().parallel()`.
Also you might notice that for small collection custom approach is slower. This can be explained by the fact that 
there is an overhead to spawn custom FJPool and new threads, while default one starts with JVM.

### Anonymous classes vs Capturing and non-capturing lambdas and method references
```
Benchmark                             (size)  Mode  Cnt      Score      Error  Units
AnonymousInterfaceBenchmark.walk      100000  avgt   50   1908.382 ±   71.845  us/op
AnonymousInterfaceBenchmark.walk     1000000  avgt   50  46063.339 ± 2006.328  us/op
LambdaBenchmark.walkCapturingLambda   100000  avgt   50   1797.824 ±   42.731  us/op
LambdaBenchmark.walkCapturingLambda  1000000  avgt   50  46714.467 ± 2494.156  us/op
LambdaBenchmark.walkInlineLambda      100000  avgt   50   1828.876 ±   11.954  us/op
LambdaBenchmark.walkInlineLambda     1000000  avgt   50  46338.504 ± 2032.320  us/op
LambdaBenchmark.walkMethodReference   100000  avgt   50   1874.930 ±   13.463  us/op
LambdaBenchmark.walkMethodReference  1000000  avgt   50  46372.412 ± 2170.001  us/op
```
Overall there is no difference as compiler optimizes this code (inline lambda into a method, capturing lambda into
`private static synthetic` static method)
Thanks @jguerra for help with Lambda benchmarks