package net.helix.pendulum.service;

import net.helix.pendulum.conf.PendulumConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;


public class APICallTest {
    
    private API api;
    
    @Before
    public void setUp() {
        PendulumConfig configuration = Mockito.mock(PendulumConfig.class);
        api = new API(new ApiArgs(configuration));
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
