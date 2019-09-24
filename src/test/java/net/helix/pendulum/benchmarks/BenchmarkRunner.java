package net.helix.pendulum.benchmarks;

import net.helix.pendulum.benchmarks.dbbenchmark.RocksDbBenchmark;
import org.junit.Assert;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

public class BenchmarkRunner {

   // @Test
    public void launchDbBenchmarks() {
        Options opts = new OptionsBuilder()
                .include(RocksDbBenchmark.class.getName() + ".*")
                .mode(Mode.Throughput) //Mode.AverageTime
                .timeUnit(TimeUnit.MILLISECONDS)
                .warmupIterations(1) //5
                .forks(1)
                .measurementIterations(1) //10
                .shouldFailOnError(true)
                .shouldDoGC(false)
                .build();

        //possible to do assertions over run results
        try {
            new Runner(opts).run();
        } catch (Throwable t) {
            Assert.fail();
        }
    }

    //@Test
    public void launchCryptoBenchmark() {
        Options opts = new OptionsBuilder()
                .include(this.getClass().getPackage().getName() + ".crypto")
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .warmupIterations(5)
                .forks(1)
                .measurementIterations(10)
                .shouldFailOnError(true)
                .shouldDoGC(false)
                .build();
        try {
            new Runner(opts).run();
        } catch (Throwable t) {
            Assert.fail();
        }
    }
}
