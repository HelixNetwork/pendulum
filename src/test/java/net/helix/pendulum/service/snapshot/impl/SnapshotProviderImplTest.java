package net.helix.pendulum.service.snapshot.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import net.helix.pendulum.conf.ConfigFactory;
import net.helix.pendulum.conf.HelixConfig;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.snapshot.SnapshotException;
import net.helix.pendulum.service.spentaddresses.SpentAddressesException;


public class SnapshotProviderImplTest {

    private SnapshotProviderImpl provider;
    private SnapshotImpl cachedBuildinSnapshot;
    
    @Before
    public void setUp(){
        provider = new SnapshotProviderImpl();
        
        // When running multiple tests, the static cached snapshot breaks this test
        cachedBuildinSnapshot = SnapshotProviderImpl.builtinSnapshot;
        SnapshotProviderImpl.builtinSnapshot = null;
    }

    @After
    public void shutdown(){
        provider.shutdown();
        
        // Set back the cached snapshot for tests after us who might use it
        SnapshotProviderImpl.builtinSnapshot = cachedBuildinSnapshot;
    }
    
    @Test
    public void testGetLatestSnapshot() throws SnapshotException, SpentAddressesException {
        HelixConfig helixConfig = ConfigFactory.createHelixConfig(true);
        provider.init(helixConfig);

        // If we run this on its own, it correctly takes the testnet milestone
        // However, running it with all tests makes it load the last global snapshot contained in the jar
        assertEquals("Initial snapshot index should be the same as the milestone start index", 
                helixConfig.getMilestoneStartIndex(), provider.getInitialSnapshot().getIndex());
        
        assertEquals("Initial snapshot timestamp should be the same as last snapshot time", 
                helixConfig.getSnapshotTime(), provider.getInitialSnapshot().getInitialTimestamp());
        
        assertEquals("Initial snapshot hash should be the genisis transaction", 
                Hash.NULL_HASH, provider.getInitialSnapshot().getHash());
        
        assertEquals("Initial provider snapshot should be equal to the latest snapshot", 
                provider.getInitialSnapshot(), provider.getLatestSnapshot());
        
        assertTrue("Initial snapshot should have a filled map of addresses", provider.getInitialSnapshot().getBalances().size() > 0);
        assertTrue("Initial snapshot supply should be equal to all supply", provider.getInitialSnapshot().hasCorrectSupply());
    }

}
