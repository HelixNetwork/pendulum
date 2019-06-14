package net.helix.hlx.benchmarks.dbbenchmark;

import net.helix.hlx.benchmarks.dbbenchmark.states.EmptyState;
import net.helix.hlx.benchmarks.dbbenchmark.states.FullState;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.persistables.Transaction;
import net.helix.hlx.storage.Indexable;
import net.helix.hlx.storage.Persistable;
import net.helix.hlx.utils.Pair;
import org.openjdk.jmh.annotations.Benchmark;

public class RocksDbBenchmark {
    @Benchmark
    public void persistOneByOne(EmptyState state) throws Exception {
        for (TransactionViewModel tvm: state.getTransactions()) {
            tvm.store(state.getTangle(), state.getSnapshotProvider().getInitialSnapshot());
        }
    }

    @Benchmark
    public void deleteOneByOne(FullState state) throws Exception {
        for (TransactionViewModel tvm : state.getTransactions()) {
            tvm.delete(state.getTangle());
        }
    }

    @Benchmark
    public void dropAll(FullState state) throws Exception {
        state.getTangle().clearColumn(Transaction.class);
        state.getTangle().clearMetadata(Transaction.class);
    }

    @Benchmark
    public void deleteBatch(FullState state) throws Exception {
        state.getTangle().deleteBatch(state.getPairs());
    }

    @Benchmark
    public void fetchOneByOne(FullState state) throws Exception {
        for (Pair<Indexable, ? extends Class<? extends Persistable>> pair : state.getPairs()) {
            state.getTangle().load(pair.hi, pair.low);
        }
    }
}
