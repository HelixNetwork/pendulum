package net.helix.pendulum.controllers;

import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.IntegerIndex;
import net.helix.pendulum.model.persistables.Round;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.HashSet;
import java.util.Random;

import static net.helix.pendulum.TransactionTestUtils.getTransactionHash;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;


public class RoundViewModelTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final Tangle tangle = new Tangle();
    private static SnapshotProvider snapshotProvider;

    private static final Random RND = new Random();


    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider;
        rocksDBPersistenceProvider =  new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),
                1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.init();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
    }

    @After
    public void shutdown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
        RoundViewModel.clear();
    }


    @Test
    public void getTest() throws Exception {
        int index = 1;
        Round round = getRound(index);
        round.set.add(getTransactionHash());
        round.set.add(getTransactionHash());
        round.set.add(getTransactionHash());
        round.set.add(getTransactionHash());
        round.set.add(getTransactionHash());
        RoundViewModel rvm = new RoundViewModel(round.index.getValue(), round.set);
        rvm.store(tangle);

        RoundViewModel result = RoundViewModel.get(tangle, index);
        Assert.assertNotNull(result);
        Assert.assertEquals(round.index.getValue(), result.index().intValue());
        Assert.assertThat(result.getHashes(), hasSize(round.set.size()));
        Assert.assertThat(result.getHashes(), contains(round.set.toArray()));
    }

    @Test
    public void deleteTest() throws Exception {
        int index = 1;
        Round round = getRound(index);
        round.set.add(getTransactionHash());
        RoundViewModel rvm = new RoundViewModel(round.index.getValue(), round.set);

        rvm.store(tangle);
        rvm.delete(tangle);
        RoundViewModel result = RoundViewModel.get(tangle, index);
        Assert.assertNull(result);

        // check cache
        rvm.store(tangle);
        boolean r = RoundViewModel.load(tangle, index);
        Assert.assertTrue(r);
        rvm.delete(tangle);
        result = RoundViewModel.get(tangle, index);
        Assert.assertNull(result);
    }

    @Test
    public void getRandomMilestoneTest() throws Exception {
        int index = 1;
        Round round = getRound(index);
        RoundViewModel rvm = new RoundViewModel(round.index.getValue(), round.set);
        Hash hash = rvm.getRandomMilestone(tangle);
        Assert.assertNull(hash);
        
        rvm.addMilestone(getTransactionHash());
        rvm.addMilestone(getTransactionHash());
        rvm.addMilestone(getTransactionHash());
        hash = rvm.getRandomMilestone(tangle);
        Assert.assertNotNull(hash);
        Assert.assertThat(rvm.getHashes(), hasItem(hash));
    }
            
    @Test
    public void firstTest() throws Exception {
        int n = 5;
        Round[] rounds = getRounds(n, 1);
        for (int i = 0; i < n; i++) {
            RoundViewModel rvm = new RoundViewModel(rounds[i].index.getValue(), rounds[i].set);
            rvm.store(tangle);
        }

        RoundViewModel result = RoundViewModel.first(tangle);
        Assert.assertNotNull(result);
        Assert.assertEquals(rounds[0].index.getValue(), result.index().intValue());
        Assert.assertThat(result.getHashes(), hasSize(rounds[0].set.size()));
        Assert.assertThat(result.getHashes(), contains(rounds[0].set.toArray()));
    }

    @Test
    public void latestTest() throws Exception {
        int n = 5;
        Round[] rounds = getRounds(n, 1);
        for (int i = 0; i < n; i++) {
            RoundViewModel rvm = new RoundViewModel(rounds[i].index.getValue(), rounds[i].set);
            rvm.store(tangle);
        }

        RoundViewModel result = RoundViewModel.latest(tangle);
        Assert.assertNotNull(result);
        Assert.assertEquals(rounds[n - 1].index.getValue(), result.index().intValue());
        Assert.assertThat(result.getHashes(), hasSize(rounds[n - 1].set.size()));
        Assert.assertThat(result.getHashes(), contains(rounds[n - 1].set.toArray()));
    }

    @Test
    public void previousTest() throws Exception {
        int n = 5;
        Round[] rounds = getRounds(n, 1);
        RoundViewModel[] roundVMs = new RoundViewModel[n];
        for (int i = 0; i < n; i++) {
            RoundViewModel rvm = new RoundViewModel(rounds[i].index.getValue(), rounds[i].set);
            roundVMs[i] = rvm;
            rvm.store(tangle);
        }

        int p = n / 2;
        RoundViewModel result = roundVMs[p].previous(tangle);
        Assert.assertNotNull(result);
        Assert.assertEquals(roundVMs[p - 1].index().intValue(), result.index().intValue());
        Assert.assertThat(result.getHashes(), hasSize(roundVMs[p - 1].getHashes().size()));
        Assert.assertThat(result.getHashes(), contains(roundVMs[p - 1].getHashes().toArray()));
    }

    @Test
    public void nextTest() throws Exception {
        int n = 5;
        Round[] rounds = getRounds(n, 1);
        RoundViewModel[] roundVMs = new RoundViewModel[n];
        for (int i = 0; i < n; i++) {
            RoundViewModel rvm = new RoundViewModel(rounds[i].index.getValue(), rounds[i].set);
            roundVMs[i] = rvm;
            rvm.store(tangle);
        }

        int p = n / 2;
        RoundViewModel result = roundVMs[p].next(tangle);
        Assert.assertNotNull(result);
        Assert.assertEquals(roundVMs[p + 1].index().intValue(), result.index().intValue());
        Assert.assertThat(result.getHashes(), hasSize(roundVMs[p + 1].getHashes().size()));
        Assert.assertThat(result.getHashes(), contains(roundVMs[p + 1].getHashes().toArray()));
    }

    @Test
    public void findClosestPrevRoundTest() throws Exception {
        int n = 6;
        int indexStep = 5;
        Round[] rounds = getRounds(n, indexStep);
        RoundViewModel[] roundVMs = new RoundViewModel[n];
        for (int i = 0; i < n; i++) {
            RoundViewModel rvm = new RoundViewModel(rounds[i].index.getValue(), rounds[i].set);
            roundVMs[i] = rvm;
            rvm.store(tangle);
        }

        int index = n / 2 * indexStep - indexStep / 2;
        RoundViewModel result = RoundViewModel.findClosestPrevRound(tangle, index, 0);
        Assert.assertNotNull(result);
        Assert.assertEquals(roundVMs[n / 2 - 1].index().intValue(), result.index().intValue());
        Assert.assertThat(result.getHashes(), hasSize(roundVMs[n / 2 - 1].getHashes().size()));
        Assert.assertThat(result.getHashes(), contains(roundVMs[n / 2 - 1].getHashes().toArray()));
    }
    
    @Test
    public void findClosestNextRoundTest() throws Exception {
        int n = 6;
        int indexStep = 5;
        Round[] rounds = getRounds(n, indexStep);
        RoundViewModel[] roundVMs = new RoundViewModel[n];
        for (int i = 0; i < n; i++) {
            RoundViewModel rvm = new RoundViewModel(rounds[i].index.getValue(), rounds[i].set);
            roundVMs[i] = rvm;
            rvm.store(tangle);
        }

        int index = n / 2 * indexStep - indexStep / 2;
        RoundViewModel result = RoundViewModel.findClosestNextRound(tangle, index, n * indexStep);
        Assert.assertNotNull(result);
        Assert.assertEquals(roundVMs[n / 2].index().intValue(), result.index().intValue());
        Assert.assertThat(result.getHashes(), hasSize(roundVMs[n / 2].getHashes().size()));
        Assert.assertThat(result.getHashes(), contains(roundVMs[n / 2].getHashes().toArray()));
    }
    

    private static Round[] getRounds(int count, int indexStep) {
        Round[] rounds = new Round[count];
        for (int i = 0; i < count; i++) {
            rounds[i] = getRound(i * indexStep);
            for (int j = 0; j < RND.nextInt(count) + 1; j++) {
                rounds[i].set.add(getTransactionHash());
            }
        }
        return rounds;
    }

    private static Round getRound(int index) {
        Round round = new Round();
        round.index = new IntegerIndex(index);
        round.set = new HashSet();
        return round;
    }
    
}
