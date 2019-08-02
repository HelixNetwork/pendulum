package net.helix.hlx.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import net.helix.hlx.conf.HelixConfig;


public class APICallTest {
    
    private API api;
    
    @Before
    public void setup() {
        HelixConfig configuration = Mockito.mock(HelixConfig.class);
        api = new API(configuration, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    @Test
    public void apiHasAllEnums() {
        for (ApiCommand c : ApiCommand.values()) {
            if (!api.commandRoute.containsKey(c)) {
                Assert.fail("Api should contain all enum values");
            }
        }
    }

}
