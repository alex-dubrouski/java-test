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
I ran these tests couple of times on my MacBook Pro 2015 [Core i7 2.2GHz, 16GB RAM]
### Notes
- Optional creates so much memory overhead that I had to rollback from Epsilon no-op to Shenandoah compacting GC to avoid OOMs [Heap is 4GB, ArrayList requires (1MM * 20bytes + some overhead) and Optional benchmark fail at 4 iteration, so ~4MM of Optionals have approximate size of 3.8GB]
- All other tests except `if vs Optional` are running no-op EpsilonGC to exclude GC overhead
- Bigger numbers mean worse result as metric is microseconds per operation (us/op)
- Size is the size of ArrayList used for benchmark (it pre-filled with `String$i` strings, where i is [0..size] to make sure GC won't do deduplication)

### Conventional If versus Optional.ofNullable().ifPresent() [Bigger is worse]
```
Benchmark                              (size)  Mode  Cnt       Score        Error  Units
IfBenchmark.walk                       100000  avgt   50     412.908 ±      7.770  us/op
IfBenchmark.walk                      1000000  avgt   50    4045.169 ±     23.630  us/op
OptionalBenchmark.walk                 100000  avgt   50     668.345 ±     19.439  us/op
OptionalBenchmark.walk                1000000  avgt   50    6905.794 ±    324.304  us/op
```

### Simple for loop versus stream().forEach() vs ArrayList.forEach() [Bigger is worse]
```
LoopBenchmark.walk                100000  avgt   50   1049.909 ±  34.355  us/op
LoopBenchmark.walk               1000000  avgt   50   9705.087 ± 160.360  us/op
CollectionForEachBenchmark.walk   100000  avgt   50   1521.542 ±  71.787  us/op
CollectionForEachBenchmark.walk  1000000  avgt   50  14889.490 ± 232.703  us/op
StreamForEachBenchmark.walk       100000  avgt   50   1560.984 ± 102.362  us/op
StreamForEachBenchmark.walk      1000000  avgt   50  14119.169 ± 465.745  us/op
```
`Collection.forEach` and `Collection.stream().forEach()` have similar performance, it can deviate, but I blame CPU
throttling on my laptop, only classic for loop is always the fastest option

### stream().parallel().forEach() versus submitting parallel tasks to custom ForkJoinPool [Bigger is worse]
```
Benchmark                              (size)  Mode  Cnt       Score        Error  Units
StreamParallelBenchmark.walk               10  avgt    5   36718.837 ±   2259.852  us/op
StreamParallelBenchmark.walk              200  avgt    5  766506.999 ± 161287.020  us/op
StreamParallelCustomTPBenchmark.walk       10  avgt    5   49163.958 ±   1385.312  us/op
StreamParallelCustomTPBenchmark.walk      200  avgt    5   50261.844 ±   2173.808  us/op
```
this test supposedly exhausts default FJPool which backs `stream().parallel()`.
Also you might notice that for small collection custom approach is slower. This can be explained by the fact that 
there is an overhead to spawn custom FJPool and new threads, while default one starts with JVM.

### Anonymous classes vs Capturing and non-capturing lambdas and method references
```
Benchmark                             (size)  Mode  Cnt      Score     Error  Units
LambdaBenchmark.walkCapturingLambda   100000  avgt   50   1375.337 ±  56.069  us/op
LambdaBenchmark.walkCapturingLambda  1000000  avgt   50  13428.373 ± 246.557  us/op
LambdaBenchmark.walkInlineLambda      100000  avgt   50   1385.110 ±  39.280  us/op
LambdaBenchmark.walkInlineLambda     1000000  avgt   50  14017.545 ± 428.827  us/op
LambdaBenchmark.walkMethodReference   100000  avgt   50   1389.873 ±  48.160  us/op
LambdaBenchmark.walkMethodReference  1000000  avgt   50  14269.167 ± 327.731  us/op
AnonymousInterfaceBenchmark.walk      100000  avgt   50   1486.847 ± 121.709  us/op
AnonymousInterfaceBenchmark.walk     1000000  avgt   50  14128.287 ± 420.497  us/op
```
Slight difference in results might be explained by CPU throttling when I run a lot of tests.
Overall there is no difference as compiler optimizes this code (inline lambda into a method, capturing lambda into
`private static synthetic` static method)