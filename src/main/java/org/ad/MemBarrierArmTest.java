package org.ad;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;


/**
 * Special version for M1 CPU with disabled LSE
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
//ArmISA 8.1 introduced LSE (CAS equivalent) and for lower core counts it proved to be alright. However it might be slower than LSE SWP in some cases
@Fork(value = 1, jvmArgsAppend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-XX:+AlwaysPreTouch", "-Xms4g", "-Xmx4g", "-XX:-UseLSE"})
public class MemBarrierArmTest {

    @Benchmark
    @Group("bhack")
    @GroupThreads(4)
    public void setB(SyncBarrierPojo syncPojo) {
        syncPojo.set(0x42 + ThreadLocalRandom.current().nextLong());
    }

    @Benchmark
    @Group("bhack")
    @GroupThreads(4)
    public void getB(SyncBarrierPojo syncPojo) {
        syncPojo.get();
    }

    @Benchmark
    @Group("fullsync")
    @GroupThreads(4)
    public void setS(SyncedPojo syncedPojo) {
        syncedPojo.set(0x42 + ThreadLocalRandom.current().nextLong());
    }

    @Benchmark
    @Group("fullsync")
    @GroupThreads(4)
    public void getS(SyncedPojo syncedPojo) {
        syncedPojo.get();
    }

    @Benchmark
    @Group("volatile")
    @GroupThreads(4)
    public void setV(SyncedVolPojo syncedPojo) {
        syncedPojo.set(0x42 + ThreadLocalRandom.current().nextLong());
    }

    @Benchmark
    @Group("volatile")
    @GroupThreads(4)
    public void getV(SyncedVolPojo syncedPojo) {
        syncedPojo.get();
    }

    /**
     * This is implementation level hack, HotSpot theoretically should acquire mem barrier
     * Semantically incorrect
     */
    @State(Scope.Benchmark)
    public static class SyncBarrierPojo {
        static volatile int SYNC_BARRIER; int sync;
        long val;
        public synchronized void set(long s) {
            this.val = s;
        }
        public long get() {
            sync = SYNC_BARRIER;
            return val;
        }
    }

    @State(Scope.Benchmark)
    public static class SyncedPojo {
        long val;
        public synchronized void set(long s) {
            this.val = s;
        }
        public synchronized long get() {
            return val;
        }
    }

    @State(Scope.Benchmark)
    public static class SyncedVolPojo {
        volatile long val;
        public void set(long s) {
            this.val = s;
        }
        public long get() {
            return val;
        }
    }
}
