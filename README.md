# java-test

## Idea
Recent Java releases added a lot of syntactical sugarcoating like `Collection.stream().forEach()` and in this repo
I would like to use OpenJDK JMH benchmarking framework to find out how it affects performance.

## Running

- To build code `$ mvn clean install`
- To run it `$ java -jar target/benchmarks.jar`
- To see list of possible options `java -jar target/benchmarks.jar -h`

## Prerequisites

Running this benchmark requires Java 11/12 as I used EpsilonGC to avoid possible GC overhead

## Test results
I ran these tests couple of times on my MacBook Pro 2015 [Core i7 2.2GHz, 16GB RAM]
### Notes
- Optional creates so much memory overhead that I had to rollback from Epsilon no-op to Shenandoah compacting GC to avoid OOMs (sic!)
- All other tests except `if vs Optional` are running no-op EpsilonGC to exclude GC overhead
- Bigger numbers mean worse result as metric is microseconds per operation (us/op)
- Size is the size of ArrayList used for benchmark (it pre-filled with `String$i` strings, where i is [0..size] to make sure GC won't do deduplication)

### Conventional If versus Optional.ofNullable().ifPresent()
```
Benchmark                              (size)  Mode  Cnt       Score        Error  Units
IfBenchmark.walk                       100000  avgt   50     412.908 ±      7.770  us/op
IfBenchmark.walk                      1000000  avgt   50    4045.169 ±     23.630  us/op
OptionalBenchmark.walk                 100000  avgt   50     668.345 ±     19.439  us/op
OptionalBenchmark.walk                1000000  avgt   50    6905.794 ±    324.304  us/op
```

### Simple for loop versus stream().forEach()
```
Benchmark                              (size)  Mode  Cnt       Score        Error  Units
LoopBenchmark.walk                     100000  avgt   50    1095.182 ±     23.729  us/op
LoopBenchmark.walk                    1000000  avgt   50   10536.424 ±    265.327  us/op
StreamForEachBenchmark.walk            100000  avgt   50    1498.113 ±     82.737  us/op
StreamForEachBenchmark.walk           1000000  avgt   50   13610.415 ±    333.496  us/op
```

### stream().parallel().forEach() versus submitting parallel tasks to custom ForkJoinPool
```
Benchmark                              (size)  Mode  Cnt       Score        Error  Units
StreamParallelBenchmark.walk               10  avgt    5   36718.837 ±   2259.852  us/op
StreamParallelBenchmark.walk              200  avgt    5  766506.999 ± 161287.020  us/op
StreamParallelCustomTPBenchmark.walk       10  avgt    5   49163.958 ±   1385.312  us/op
StreamParallelCustomTPBenchmark.walk      200  avgt    5   50261.844 ±   2173.808  us/op
```
this test supposedly exhausts default FJPool which backs `stream().parallel()`.
Also you might notice that for small collection custom approach is slower. This can be explained by the fact that 
there is an overhead to spawn custom FJPool, while default one starts with JVM.