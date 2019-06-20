package net.helix.hlx.utils;

import java.util.HashMap;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.idm.X509CertificateCredential;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;


public class MapIdentityManagerTest {
    
    private final String testUser = "testUser";
    private final char[] testPassword = "testPassword".toCharArray();

    @Test
    public void verifyAccountWithClearTextPasswordTest() {
        HashMap<String, char[]> users = new HashMap<>();
        users.put(testUser, testPassword);
        MapIdentityManager identityManager = new MapIdentityManager(users);
        Account account = identityManager.verify(testUser, new PasswordCredential(testPassword));

        assertEquals("testUser needs equal to user returned by account", testUser, account.getPrincipal().getName());
        assertThat("Roles must not be implemented", account.getRoles(), is(empty()));
    }

    @Test
    public void verifyAccountWithHex() {
        HashMap<String, char[]> users = new HashMap<>();
        users.put(testUser, "1502ddbd9e4262cdae9dde5c3709a64ea4aa38418452474016ff6d97770173cc".toCharArray());
        MapIdentityManager identityManager = new MapIdentityManager(users);
        Account account = identityManager.verify(testUser, new PasswordCredential(testPassword));

        assertEquals("testUser needs equal to user returned by account", testUser, account.getPrincipal().getName());
    }

    @Test
    public void verifyAccountWithUnsupportedCredentialTypeTest() {
        HashMap<String, char[]> users = new HashMap<>();
        users.put(testUser, testPassword);
        MapIdentityManager identityManager = new MapIdentityManager(users);
        Account account = identityManager.verify(testUser, new X509CertificateCredential(null));

        assertNull("Must be null, because credential type is not supported", account);
    }

}
