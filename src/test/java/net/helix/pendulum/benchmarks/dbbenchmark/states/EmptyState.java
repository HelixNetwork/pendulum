package net.helix.pendulum.benchmarks.dbbenchmark.states;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class EmptyState extends DbState {

    @Override
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @TearDown(Level.Trial)
    public void shutdown() throws Exception {
        super.shutdown();
    }

    @Override
    @TearDown(Level.Iteration)
    public void clearDb() throws Exception {
        super.clearDb();
    }

}