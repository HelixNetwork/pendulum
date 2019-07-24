package net.helix.hlx.utils;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import net.helix.hlx.crypto.Sha3;
import org.bouncycastle.util.encoders.Hex;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class MapIdentityManager implements IdentityManager {

    private final Map<String, char[]> users;

    public MapIdentityManager(final Map<String, char[]> users) {
        this.users = users;
    }

    @Override
    public Account verify(Account account) {
        // An existing account so for testing assume still valid.
        return account;
    }

    @Override
    public Account verify(String id, Credential credential) {
        Account account = getAccount(id);
        if (account != null && verifyCredential(account, credential)) {
            return account;
        }

        return null;
    }

    @Override
    public Account verify(Credential credential) {
        // TODO Auto-generated method stub
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential) {
        if (credential instanceof PasswordCredential) {
            char[] givenPassword = ((PasswordCredential) credential).getPassword();
            char[] expectedPassword = users.get(account.getPrincipal().getName()); 
            boolean verified = Arrays.equals(givenPassword, expectedPassword);
            // Password can either be clear text or the crypto of the password
            if (!verified) {
                byte[] bytes = new String(givenPassword).getBytes();
                givenPassword = Hex.toHexString(Sha3.getStandardHash(bytes)).toCharArray();
                verified = Arrays.equals(givenPassword, expectedPassword);
            }            
            return verified;
        }
        return false;
    }

    private Account getAccount(final String id) {
        if (users.containsKey(id)) {
            return new Account() {

                private final Principal principal = new Principal() {

                    @Override
                    public String getName() {
                        return id;
                    }
                };

                @Override
                public Principal getPrincipal() {
                    return principal;
                }

                @Override
                public Set<String> getRoles() {
                    return Collections.emptySet();
                }

            };
        }
        return null;
    }

}