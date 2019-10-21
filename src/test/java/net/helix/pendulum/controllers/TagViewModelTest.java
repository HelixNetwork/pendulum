package net.helix.pendulum.controllers;

import net.helix.pendulum.conf.MainnetConfig;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.impl.SnapshotProviderImpl;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static net.helix.pendulum.TransactionTestUtils.getTransactionHash;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;


public class TagViewModelTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final Tangle tangle = new Tangle();
    private static SnapshotProvider snapshotProvider;


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
    }

    @Test
    public void storeAndLoadTest() throws Exception {
        Hash hash = HashFactory.TAG.create("010203");
        TagViewModel tagVM = new TagViewModel(hash);
        tagVM.addHash(getTransactionHash());
        tagVM.addHash(getTransactionHash());
        tagVM.addHash(getTransactionHash());
        tagVM.store(tangle);

        TagViewModel _tagVM = TagViewModel.load(tangle, hash);
        Assert.assertEquals(tagVM.getIndex(), _tagVM.getIndex());
        Assert.assertThat(_tagVM.getHashes(), hasSize(tagVM.getHashes().size()));
        Assert.assertThat(_tagVM.getHashes(), contains(tagVM.getHashes().toArray()));
    }

    @Test
    public void deleteTest() throws Exception {
        Hash hash = HashFactory.TAG.create("01020304");
        TagViewModel tagVM = new TagViewModel(hash);
        tagVM.addHash(getTransactionHash());
        tagVM.addHash(getTransactionHash());
        tagVM.addHash(getTransactionHash());
        tagVM.store(tangle);

        TagViewModel _tagVM = TagViewModel.load(tangle, hash);
        Assert.assertEquals(tagVM.getIndex(), _tagVM.getIndex());
        Assert.assertThat(_tagVM.getHashes(), hasSize(tagVM.getHashes().size()));
        Assert.assertThat(_tagVM.getHashes(), contains(tagVM.getHashes().toArray()));

        tagVM.delete(tangle);
        _tagVM = TagViewModel.load(tangle, hash);
        Assert.assertEquals(tagVM.getIndex(), _tagVM.getIndex());
        Assert.assertThat(_tagVM.getHashes(), hasSize(0));
    }

    @Test
    public void firstTest() throws Exception {
        int n = 5;
        TagViewModel tagVM;
        TagViewModel[] tagVMs = new TagViewModel[n];
        for (int i = 0; i < n; i++) {
            tagVM = new TagViewModel(HashFactory.TAG.create("0" + i));
            tagVM.addHash(getTransactionHash());
            tagVM.store(tangle);
            tagVMs[i] = tagVM;
        }

        tagVM = TagViewModel.first(tangle);
        Assert.assertNotNull(tagVM);
        Assert.assertEquals(tagVMs[0].getIndex(), tagVM.getIndex());
        Assert.assertThat(tagVM.getHashes(), hasSize(tagVMs[0].getHashes().size()));
        Assert.assertThat(tagVM.getHashes(), contains(tagVMs[0].getHashes().toArray()));
    }

    @Test
    public void nextTest() throws Exception {
        int n = 5;
        TagViewModel tagVM;
        TagViewModel[] tagVMs = new TagViewModel[n];
        for (int i = 0; i < n; i++) {
            tagVM = new TagViewModel(HashFactory.TAG.create("0" + i));
            tagVM.addHash(getTransactionHash());
            tagVM.store(tangle);
            tagVMs[i] = tagVM;
        }

        int p = n / 2;
        tagVM = tagVMs[p].next(tangle);
        Assert.assertNotNull(tagVM);
        Assert.assertEquals(tagVMs[p + 1].getIndex(), tagVM.getIndex());
        Assert.assertThat(tagVM.getHashes(), hasSize(tagVMs[p + 1].getHashes().size()));
        Assert.assertThat(tagVM.getHashes(), contains(tagVMs[p + 1].getHashes().toArray()));
    }
    
}
