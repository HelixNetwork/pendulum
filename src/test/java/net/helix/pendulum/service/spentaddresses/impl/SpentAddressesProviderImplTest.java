package net.helix.pendulum.service.spentaddresses.impl;

import net.helix.pendulum.TransactionTestUtils;
import net.helix.pendulum.conf.ConfigFactory;
import net.helix.pendulum.model.Hash;

import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertTrue;


public class SpentAddressesProviderImplTest {
    
    private static final Hash A = TransactionTestUtils.getTransactionHash();
    private static final Hash B = TransactionTestUtils.getTransactionHash();
    
    private SpentAddressesProviderImpl provider;
    
    @Before
    public void setUp() throws Exception {
        provider = new SpentAddressesProviderImpl();
        provider.init(ConfigFactory.createPendulumConfig(false));
    }

    @After
    public void shutdown() {
        if (provider.rocksDBPersistenceProvider != null) {
            provider.rocksDBPersistenceProvider.shutdown();
        }
    }

    @Test
    public void saveAddressTest() throws Exception {
        provider.saveAddress(A);
        assertTrue(provider.containsAddress(A));
    }

    @Test
    public void saveAddressesBatchTest() throws Exception {
        List<Hash> addresses = new LinkedList<>();
        addresses.add(A);
        addresses.add(B);
        
        provider.saveAddressesBatch(addresses);
        for (Hash address : addresses) {
            assertTrue(provider.containsAddress(address));
        }
    }

}
