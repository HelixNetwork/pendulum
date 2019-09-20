package net.helix.pendulum.benchmarks.dbbenchmark.states;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.persistables.Transaction;
import net.helix.pendulum.storage.Indexable;
import net.helix.pendulum.storage.Persistable;
import net.helix.pendulum.utils.Pair;
import org.openjdk.jmh.annotations.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
public class FullState extends DbState {

    private List<Pair<Indexable, ? extends Class<? extends Persistable>>> pairs;

    @Override
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        super.setUp();
        pairs = getTransactions().stream()
                .map(tvm -> new Pair<>((Indexable) tvm.getHash(), Transaction.class))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

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

    @Setup(Level.Iteration)
    public void populateDb() throws Exception {
        System.out.println("-----------------------iteration setup--------------------------------");
        for (TransactionViewModel tvm : getTransactions()) {
            tvm.store(getTangle(), getSnapshotProvider().getInitialSnapshot());
        }
    }

    public List<Pair<Indexable, ? extends Class<? extends Persistable>>> getPairs() {
        return pairs;
    }
}
