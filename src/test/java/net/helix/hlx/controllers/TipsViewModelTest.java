package net.helix.hlx.controllers;

import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import net.helix.hlx.model.Hash;
import static net.helix.hlx.TransactionTestUtils.getTransactionHash;


public class TipsViewModelTest {

    @Before
    public void setup() throws Exception {

    }

    @After
    public void shutdown() throws Exception {

    }

    @Test
    public void addTipHash() throws Exception {

    }

    @Test
    public void removeTipHash() throws Exception {

    }

    @Test
    public void setSolid() throws Exception {

    }

    @Test
    public void getTips() throws Exception {

    }

    @Test
    public void getRandomSolidTipHash() throws Exception {

    }

    @Test
    public void getRandomNonSolidTipHash() throws Exception {

    }

    @Test
    public void getRandomTipHash() throws Exception {

    }

    @Test
    public void nonSolidSize() throws Exception {

    }

    @Test
    public void size() throws Exception {

    }

    @Test
    public void loadTipHashes() throws Exception {

    }

    @Test
    public void nonsolidCapacityLimitedTest() throws ExecutionException, InterruptedException {
        TipsViewModel tipsVM = new TipsViewModel();
        int capacity = TipsViewModel.MAX_TIPS;
        //fill tips list
        for (int i = 0; i < capacity * 2 ; i++) {
            Hash hash = getTransactionHash();
            tipsVM.addTipHash(hash);
        }
        //check that limit wasn't breached
        Assert.assertEquals(capacity, tipsVM.nonSolidSize());
    }

    @Test
    public void solidCapacityLimitedTest() throws ExecutionException, InterruptedException {
        TipsViewModel tipsVM = new TipsViewModel();
        int capacity = TipsViewModel.MAX_TIPS;
        //fill tips list
        for (int i = 0; i < capacity * 2 ; i++) {
            Hash hash = getTransactionHash();
            tipsVM.addTipHash(hash);
            tipsVM.setSolid(hash);
        }
        //check that limit wasn't breached
        Assert.assertEquals(capacity, tipsVM.size());
    }

    @Test
    public void totalCapacityLimitedTest() throws ExecutionException, InterruptedException {
        TipsViewModel tipsVM = new TipsViewModel();
        int capacity = TipsViewModel.MAX_TIPS;
        //fill tips list
        for (int i = 0; i <= capacity * 4; i++) {
            Hash hash = getTransactionHash();
            tipsVM.addTipHash(hash);
            if (i % 2 == 1) {
                tipsVM.setSolid(hash);
            }
        }
        //check that limit wasn't breached
        Assert.assertEquals(capacity * 2, tipsVM.size());
    }

}
