# java-test

## Idea
This is a collection of different benchmarks for different JDK features and frameworks

## Running

- To build code `$ mvn clean install`
- To run it `$ java -jar target/benchmarks.jar`
- To see list of possible options `java -jar target/benchmarks.jar -h`

## Prerequisites

- Benchmark code was updated to OJDK18. Added benchmark for JEP-417 
- Benchmark code was updated to OJDK17. Results listed here we measured with OJDK14 and 15
plus new benchmarks will use MemoryHandles (JEP-370) and preview features which are only available in Java 14+ 
- Older tests were executed with AdoptOpenJDK `JDK 12.0.2, OpenJDK 64-Bit Server VM, 12.0.2+10`, hsdis compiled from OpenJDK source (binutils 2.32), 

## Interesting findings

### Auto vectorization in JDK18 works better than new Vector API
I used simple example described in JEP-417 for benchmarking. Surprisingly, scalar code was auto vectorized resulting in better performance than its' Vector counterpart
```
Benchmark                            (size)  Mode  Cnt      Score      Error  Units
VectorBenchmark.scalarComputation     64000  avgt   10     28.391 ±    0.522  us/op
VectorBenchmark.scalarComputation  64000000  avgt   10  67771.693 ± 2875.739  us/op
VectorBenchmark.vectorComputation     64000  avgt   10     50.042 ±    0.805  us/op
VectorBenchmark.vectorComputation  64000000  avgt   10  88617.490 ± 2072.552  us/op
```
Check [ASM code](docs/). Scalar method is auto-vectorized with minimum of overhead, while Vector API has a lot of additional checks and computations

### MemoryHandles[deleted] benchmark performance drops significantly during warmup for direct ByteBuffer running on ShenandoahGC on JDK18
Performance abruptly drops at iteration 7 of the warmup and only on ShenandoahGC running JDK18. JDK11/JDK17 do not suffer from the same problem
Running on G1GC or ParallelGC also does not show any signs of degradation
```
# VM version: JDK 18.0.1, OpenJDK 64-Bit Server VM, 18.0.1+10
# VM options: -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -Xms8g -Xmx8g -Xlog:gc*,gc+age=trace,gc+phases=debug:file=gc.log:tags,uptime,time,level:filecount=5,filesize=100M -XX:MaxDirectMemorySize=4G
...
# Benchmark: org.ad.MemoryHandlesBenchmark.rwBBD

# Warmup Iteration   1: 248.819 us/op
...
# Warmup Iteration   7: 387.206 us/op
# Warmup Iteration   8: 389.188 us/op
...
Iteration  10: 394.420 us/op
```
Profiling with perfasm shows that after recompilation (there is switch to a new code version at around iteration 7 of warmup) new code started loading quite a few absolute data moves prepending field loads. I.e.
```
Before
  1.08%  │  ││  0x00007fd8500eb61c:   mov    0x1c(%r11),%ecx          ;*getfield limit {reexecute=0 rethrow=0 return_oop=0}
After
  4.34%  │  0x00007f63ccf8ce32:   movabs $0x602032dc0,%r10            ;   {oop(a &apos;java/nio/DirectByteBuffer&apos;{0x0000000602032dc0})}
  0.13%  │  0x00007f63ccf8ce3c:   mov    0x1c(%r10),%edi              ;*getfield limit {reexecute=0 rethrow=0 return_oop=0}

```
causing significant degradation of performance. What's surprising - it does not happen on other garbage collectors

## Test results
I ran these tests on idle development server [2x AMD Opteron(tm) Processor 6328, 256GB]
### Notes
- Optional creates so much memory overhead that I had to rollback from Epsilon no-op to Shenandoah compacting GC to avoid OOMs [Allocating Optional objects requires a lot of memory]
- Most of the tests except `Optional` and `Strings` are running no-op EpsilonGC to exclude GC overhead
- Bigger numbers mean worse result as metric is microseconds per operation (us/op)
- Size is the size of ArrayList used for benchmark (it pre-filled with `String$i` strings, where i is [0..size] to make sure GC won't do deduplication)
- Tests do not produce assembly code by default, but I captured hot spots with help of `-prof perfasm` and added text files with assembly code, you can find it here [assembly code](docs/)

### Obvious test: traversing ArrayList vs LinkedList (accessing LinkedList elements by index is O(n)), for(){} loop vs forEach will be explained later
```
Benchmark                                      (size)  Mode  Cnt         Score        Error  Units
ArrayLinkedListBenchmark.arrayListAdd          100000    ss    5      6156.139 ±   1793.898  us/op
ArrayLinkedListBenchmark.linkedListAdd         100000    ss    5      6409.345 ±    822.076  us/op
ArrayLinkedListBenchmark.traverseAList         100000    ss    5       843.464 ±   1042.563  us/op
ArrayLinkedListBenchmark.traverseLList         100000    ss    5  21241805.077 ± 321046.934  us/op
ArrayLinkedListBenchmark.traverseAListForEach  100000    ss    5      1981.504 ±   1920.284  us/op
ArrayLinkedListBenchmark.traverseLListForEach  100000    ss    5      1999.166 ±    927.983  us/op
```

### Conventional if(){} versus Optional.ofNullable().ifPresent() vs stream().filter(Objects::nonNull).forEach() [Bigger is worse]
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

### Simple for(){} loop versus stream().forEach() vs ArrayList.forEach() [Bigger is worse]
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

### Anonymous classes vs capturing and non-capturing lambdas and method references
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

### Compiler time optimizations (Lock Elision and Scalar Replacements on and off)

This is not strictly a benchmark, but rather tricky test to show how compile time optimizations affect performance.
 This test was inspired by set of articles from Aleksey Shipilev:
- https://shipilev.net/jvm/anatomy-quarks/18-scalar-replacement/
- https://shipilev.net/jvm/anatomy-quarks/19-lock-elision/
Both test classes are the same with only one small difference, second class has this command line flag `-XX:-DoEscapeAnalysis`. Please be careful as `LockElisionScalarReplacementNoEA` 
requires 30GB heap to finish.
#### Escape Analysis enabled
I deliberately used pretty dumb code with irrelevant class method and synchronization block, smart compiler compacts entire class with sync on local object to this
```
  0.22%  ↗  0x00007fab7447a830: mov    %r9,(%rsp)
         │  0x00007fab7447a834: mov    0x38(%rsp),%rsi
         │  0x00007fab7447a839: mov    $0x43,%edx           ;Yes, yes, entire page of Java code, which includes 2 classes compacted into 1 constant
  6.82%  │  0x00007fab7447a83e: nop
  0.65%  │  0x00007fab7447a83f: callq  0x00007fab6c8ced80   ; ImmutableOopMap{[48]=Oop [56]=Oop [64]=Oop [0]=Oop }
         │                                                  ;*invokevirtual consume {reexecute=0 rethrow=0 return_oop=0}
         │                                                  ; - org.ad.LockElisionScalarReplacement::classMethod@14 (line 54)
         │                                                  ; - org.ad.generated.LockElisionScalarReplacement_classMethod_jmhTest::classMethod_avgt_jmhStub@17 (line 190)
         │                                                  ;   {optimized virtual_call}
  7.55%  │  0x00007fab7447a844: mov    (%rsp),%r9
  0.16%  │  0x00007fab7447a848: movzbl 0x94(%r9),%r8d       ;*ifeq {reexecute=0 rethrow=0 return_oop=0}
         │                                                  ; - org.ad.generated.LockElisionScalarReplacement_classMethod_jmhTest::classMethod_avgt_jmhStub@30 (line 192)
  7.92%  │  0x00007fab7447a850: mov    0x108(%r15),%r10
  0.75%  │  0x00007fab7447a857: add    $0x1,%rbp            ; ImmutableOopMap{r9=Oop [48]=Oop [56]=Oop [64]=Oop }
         │                                                  ;*ifeq {reexecute=1 rethrow=0 return_oop=0}
         │                                                  ; - org.ad.generated.LockElisionScalarReplacement_classMethod_jmhTest::classMethod_avgt_jmhStub@30 (line 192)
         │  0x00007fab7447a85b: test   %eax,(%r10)          ;   {poll}
         │  0x00007fab7447a85e: test   %r8d,%r8d
  7.69%  ╰  0x00007fab7447a861: je     0x00007fab7447a830   ;*aload_1 {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - org.ad.generated.LockElisionScalarReplacement_classMethod_jmhTest::classMethod_avgt_jmhStub@33 (line 193)
```
#### Escape Analysis disabled
Oh, this get's ugly immediately
- First of all allocating `new Object()` / `new SomeWork(1)` for synchronization block causes terrible memory allocation
- Assembly code now contains a lot of additional code
Simple class method ([Full assembly code](docs/compiler-optimisations/scalar-replacement-and-lock-elision-128M-heap.txt))
```
  4.49%  ↗  0x00007f175447a0e0: mov    %r10,0x118(%r15)
  0.23%  │  0x00007f175447a0e7: mov    0x8(%rsp),%r11
  0.04%  │  0x00007f175447a0ec: mov    0xb8(%r11),%r10
  4.20%  │  0x00007f175447a0f3: mov    %r10,(%rax)
  9.89%  │  0x00007f175447a0f6: movl   $0x234357,0x8(%rax) ;   {metadata(&apos;org/ad/LockElisionScalarReplacementNoEA$SomeWork&apos;)}
  5.98%  │  0x00007f175447a0fd: mov    %r8,(%rsp)          ;*new {reexecute=0 rethrow=0 return_oop=0}                 <------- instantiating class
         │                                                 ; - org.ad.LockElisionScalarReplacementNoEA::classMethod@1 (line 54)
         │                                                 ; - org.ad.generated.LockElisionScalarReplacementNoEA_classMethod_jmhTest::classMethod_avgt_jmhStub@17 (line 190)
  4.72%  │  0x00007f175447a101: movl   $0x1,0xc(%rax)      ;*invokevirtual doAdd {reexecute=0 rethrow=0 return_oop=0} <------- invoking class method
         │                                                 ; - org.ad.LockElisionScalarReplacementNoEA::classMethod@11 (line 54)
         │                                                 ; - org.ad.generated.LockElisionScalarReplacementNoEA_classMethod_jmhTest::classMethod_avgt_jmhStub@17 (line 190)
  1.55%  │  0x00007f175447a108: mov    0x38(%rsp),%rsi
         │  0x00007f175447a10d: mov    $0x43,%edx
  4.33%  │  0x00007f175447a112: nop
  0.02%  │  0x00007f175447a113: callq  0x00007f174c8ced80  ; ImmutableOopMap{[48]=Oop [56]=Oop [64]=Oop [0]=Oop }
         │                                                 ;*invokevirtual consume {reexecute=0 rethrow=0 return_oop=0}
         │                                                 ; - org.ad.LockElisionScalarReplacementNoEA::classMethod@14 (line 54)
         │                                                 ; - org.ad.generated.LockElisionScalarReplacementNoEA_classMethod_jmhTest::classMethod_avgt_jmhStub@17 (line 190)
         │                                                 ;   {optimized virtual_call}
  4.51%  │  0x00007f175447a118: mov    (%rsp),%r8
  0.43%  │  0x00007f175447a11c: movzbl 0x94(%r8),%r11d     ;*ifeq {reexecute=0 rethrow=0 return_oop=0}
         │                                                 ; - org.ad.generated.LockElisionScalarReplacementNoEA_classMethod_jmhTest::classMethod_avgt_jmhStub@30 (line 192)
  4.53%  │  0x00007f175447a124: mov    0x108(%r15),%r10
  0.08%  │  0x00007f175447a12b: add    $0x1,%rbp           ; ImmutableOopMap{r8=Oop [48]=Oop [56]=Oop [64]=Oop }
         │                                                 ;*ifeq {reexecute=1 rethrow=0 return_oop=0}
         │                                                 ; - org.ad.generated.LockElisionScalarReplacementNoEA_classMethod_jmhTest::classMethod_avgt_jmhStub@30 (line 192)
         │  0x00007f175447a12f: test   %eax,(%r10)         ;   {poll}
         │  0x00007f175447a132: test   %r11d,%r11d
  4.55%  │  0x00007f175447a135: jne    0x00007f175447a0aa
  0.06%  │  0x00007f175447a13b: mov    0x118(%r15),%rax
         │  0x00007f175447a142: mov    %rax,%r10
         │  0x00007f175447a145: add    $0x10,%r10          ;*ifeq {reexecute=0 rethrow=0 return_oop=0}
         │                                                 ; - org.ad.generated.LockElisionScalarReplacementNoEA_classMethod_jmhTest::classMethod_avgt_jmhStub@30 (line 192)
  4.51%  │  0x00007f175447a149: cmp    0x128(%r15),%r10
  0.21%  ╰  0x00007f175447a150: jb     0x00007f175447a0e0
```
Class method with synchronization block ([Full assembly code](docs/compiler-optimisations/scalar-replacement-and-lock-elision-128M-heap.txt))
```
  1.07%  │  0x00007f824447bb26: movl   $0x684,0x8(%rdi)         ;   {metadata(&apos;java/lang/Object&apos;)}
  1.54%  │  0x00007f824447bb2d: movl   $0x0,0xc(%rdi)           ;<- instantiating lock Object *new {reexecute=0 rethrow=0 return_oop=0}
...
  0.90%  │  0x00007f824447bb5b: test   $0xffffffffffffff87,%rdx
         │  0x00007f824447bb62: jne    0x00007f824447bdac       ;*monitorenter {reexecute=0 rethrow=0 return_oop=0}  <---- Entering sync block
         │                                                      ; - org.ad.LockElisionScalarReplacementNoEA$SomeWork::doSyncAdd@9 (line 71)
         │                                                      ; - org.ad.LockElisionScalarReplacementNoEA::syncClassMethod@11 (line 59)
         │                                                      ; - org.ad.generated.LockElisionScalarReplacementNoEA_syncClassMethod_jmhTest::syncClassMethod_avgt_jmhStub@17 (line 190)
         │  0x00007f824447bb68: mov    $0x42,%r10d              ;<-- Adding 0x42
  0.99%  │  0x00007f824447bb6e: add    0xc(%rcx),%r10d
  0.29%  │  0x00007f824447bb72: mov    $0x7,%ecx
         │  0x00007f824447bb77: and    (%rdi),%rcx
  0.02%  │  0x00007f824447bb7a: cmp    $0x5,%rcx
  0.92%  │  0x00007f824447bb7e: jne    0x00007f824447bd0d
         │  0x00007f824447bb84: mov    %r8,0x8(%rsp)
  1.23%  │  0x00007f824447bb89: mov    %r9,0x10(%rsp)           ;*monitorexit {reexecute=0 rethrow=0 return_oop=0}   <---- exiting sync block
```
and a lot of other things happening

### Synchronization test
Here I am trying to find the difference between different ways to synchronize access to fields (inspired by https://shipilev.net/blog/2014/jmm-pragmatics/). 
There are three different implementation of a simple POJO, one fully `synchronized`, second with memory barrier hack (theoretically should work in HotSpot JVM, 
but not guaranteed). Third one is proper implementation with volatile field
```
Benchmark                     Mode  Cnt   Score    Error  Units
MemBarrierTest.bhack:getB     avgt   10   0.014 ±  0.005  us/op
MemBarrierTest.bhack:setB     avgt   10  15.515 ±  3.193  us/op
MemBarrierTest.fullsync:getS  avgt   10   3.492 ±  0.272  us/op
MemBarrierTest.fullsync:setS  avgt   10   3.909 ±  0.438  us/op
MemBarrierTest.volatile:getV  avgt   10   0.001 ±  0.001  us/op
MemBarrierTest.volatile:setV  avgt   10   1.845 ±  0.674  us/op
```
Assembly code makes me think barrier hack does not work (in OpenJDK12), as it is optimized into simple static field access

### Strings test
With AdoptOpenJDK12
```
1st run
Benchmark                          Mode    Cnt   Score   Error  Units
StringBenchmark.testConcat           ss  10000  16.827 ± 1.614  us/op
StringBenchmark.testDummyConcat      ss  10000   0.153 ± 0.005  us/op
StringBenchmark.testDummySB          ss  10000   0.490 ± 0.028  us/op
StringBenchmark.testIntConcat        ss  10000   0.437 ± 0.056  us/op
StringBenchmark.testIntToString      ss  10000   0.337 ± 0.065  us/op
StringBenchmark.testIntToString2     ss  10000   0.255 ± 0.013  us/op
StringBenchmark.testStringBuffer     ss  10000   1.131 ± 0.047  us/op
StringBenchmark.testStringBuilder    ss  10000   1.073 ± 0.073  us/op

2nd run
Benchmark                          Mode   Cnt  Score   Error  Units
StringBenchmark.testConcat           ss  1000  4.340 ± 0.236  us/op
StringBenchmark.testDummyConcat      ss  1000  0.110 ± 0.005  us/op
StringBenchmark.testDummySB          ss  1000  0.127 ± 0.006  us/op
StringBenchmark.testIntConcat        ss  1000  0.115 ± 0.002  us/op
StringBenchmark.testIntToString      ss  1000  0.182 ± 0.025  us/op
StringBenchmark.testIntToString2     ss  1000  0.228 ± 0.028  us/op
StringBenchmark.testStringBuffer     ss  1000  1.371 ± 0.084  us/op
StringBenchmark.testStringBuilder    ss  1000  1.489 ± 0.108  us/op
```
This test has huge granularity, results might differ from run to run significantly and they are different between OpenJDK v11 and v12
Even very long warm up phase does not help
(inspired by Alex Shipilev blog)

### Volatiles test
```
Benchmark                     (size)  Mode  Cnt      Score      Error  Units
VolatileBenchmark.getI         10000    ss   50     40.041 ±    1.297  us/op
VolatileBenchmark.getI        100000    ss   50    414.367 ±   15.535  us/op
VolatileBenchmark.getVI        10000    ss   50     41.386 ±    8.148  us/op
VolatileBenchmark.getVI       100000    ss   50    439.990 ±   16.668  us/op
VolatileBenchmark.setI         10000    ss   50      1.060 ±    0.201  us/op
VolatileBenchmark.setI        100000    ss   50      6.970 ±    1.052  us/op
VolatileBenchmark.setVI        10000    ss   50   3752.773 ±  338.589  us/op
VolatileBenchmark.setVI       100000    ss   50  61828.469 ± 2549.637  us/op
```
Java's implementation of volatile emits memory barrier after write in a form of `lock addl %(rsp-offset), $0`
(inspired by Alex Shipilev blog)
Added a loop to track assembly code hot spots

### VarHandlesBenchmark
```
Benchmark                                  (size)  Mode  Cnt       Score       Error  Units
VarHandlesBenchmark.array_byte            1000000  avgt   10  302359.208 ± 24750.815  ns/op
VarHandlesBenchmark.byteBuffer_byte       1000000  avgt   10  427770.862 ±  5294.746  ns/op
VarHandlesBenchmark.byteBuffer_long       1000000  avgt   10   75093.438 ±  1115.174  ns/op
VarHandlesBenchmark.unsafe_long           1000000  avgt   10   75885.083 ±  2496.315  ns/op
VarHandlesBenchmark.varHandles            1000000  avgt   10   75074.925 ±  1551.601  ns/op
```
Executed with OpenJDK 13 (includes Java 9 fixes for ByteBuffer) on Dell desktop with Xeon 4108 CPU. Test updated to include changes to Unsafe implementation

### MonitorRLBenchmark
Tests ReentrantLock in two modes against fat monitor + RTMLocking
```
Benchmark                            Mode  Cnt     Score    Error  Units
MonitorRLBenchmark.fairrelock        avgt   20  3319.645 ± 25.871  us/op
MonitorRLBenchmark.monitor           avgt   20    93.096 ± 13.944  us/op
MonitorRLBenchmark.monitorbiased     avgt   20    93.810 ± 14.230  us/op
MonitorRLBenchmark.monitorbiasedrtm  avgt   20     2.341 ±  0.098  us/op
MonitorRLBenchmark.monitorrtm        avgt   20     2.477 ±  0.078  us/op
MonitorRLBenchmark.unfairrelock      avgt   20    19.924 ±  0.691  us/op
```
### VHReflectionBenchmark
Testing class instantiation and setting private field value via Reflection and VarHandle (Escape Analysis disabled to avoid over optimization)
```
Benchmark                                  Mode  Cnt   Score   Error  Units
VHReflectionBenchmark.testReflection       avgt   10  72.162 ± 2.562  ns/op
VHReflectionBenchmark.testVH               avgt   10   3.528 ± 0.059  ns/op
``` 

### MemoryHandlesBenchmark (deleted due to unstable API)
This benchmark is not verified. Might include some issues, though ASM code looks pretty clean
```
Benchmark                          Mode  Cnt    Score     Error  Units
MemoryHandlesBenchmark.rwBBD       avgt    5  576.576 ±   5.050  us/op
MemoryHandlesBenchmark.rwBBI       avgt    5  660.252 ± 104.421  us/op
MemoryHandlesBenchmark.rwMH        avgt    5  479.551 ±  18.959  us/op
```