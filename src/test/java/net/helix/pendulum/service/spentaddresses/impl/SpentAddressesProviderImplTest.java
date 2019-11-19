package net.helix.pendulum.service.spentaddresses.impl;

import net.helix.pendulum.TransactionTestUtils;
import net.helix.pendulum.conf.ConfigFactory;
import net.helix.pendulum.model.Hash;

import java.util.LinkedList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertTrue;


public class SpentAddressesProviderImplTest {
    
    private static final Hash A = TransactionTestUtils.getTransactionHash();
    private static final Hash B = TransactionTestUtils.getTransactionHash();
    
    private static SpentAddressesProviderImpl provider;
    
    @BeforeClass
    public static void setUp() throws Exception {
        provider = SpentAddressesProviderImpl.getInstance();
        provider.init(ConfigFactory.createPendulumConfig(true));
    }

    @AfterClass
    public static void shutdown() {
        provider.shutdown();
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
