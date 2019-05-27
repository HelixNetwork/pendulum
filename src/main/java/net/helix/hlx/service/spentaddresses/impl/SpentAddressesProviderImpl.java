package net.helix.hlx.service.spentaddresses.impl;

import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.conf.SnapshotConfig;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.model.persistables.SpentAddress;
import net.helix.hlx.service.spentaddresses.SpentAddressesException;
import net.helix.hlx.service.spentaddresses.SpentAddressesProvider;
import net.helix.hlx.storage.Indexable;
import net.helix.hlx.storage.Persistable;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.storage.rocksDB.RocksDBPersistenceProvider;
import net.helix.hlx.utils.Pair;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Implementation of <tt>SpentAddressesProvider</tt>.
 * Addresses are saved/found on the {@link Tangle}.
 * The folder location is provided by {@link HelixConfig#getLocalSnapshotsBasePath()}
 *
 */
public class SpentAddressesProviderImpl implements SpentAddressesProvider {
    private static final Logger log = LoggerFactory.getLogger(SpentAddressesProviderImpl.class);

    private RocksDBPersistenceProvider rocksDBPersistenceProvider;

    private SnapshotConfig config;

    /**
     * Creates a new instance of SpentAddressesProvider
     */
    public SpentAddressesProviderImpl() {
        this.rocksDBPersistenceProvider = new RocksDBPersistenceProvider(SPENT_ADDRESSES_DB,
                SPENT_ADDRESSES_LOG, 1000,
                new HashMap<String, Class<? extends Persistable>>(1)
                {{put("spent-addresses", SpentAddress.class);}}, null);
    }

    /**
     * Starts the SpentAddressesProvider by reading the previous spent addresses from files.
     *
     * @param config The snapshot configuration used for file location
     * @return the current instance
     * @throws SpentAddressesException if we failed to create a file at the designated location
     */
    public SpentAddressesProviderImpl init(SnapshotConfig config)
            throws SpentAddressesException {
        this.config = config;
        try {
            this.rocksDBPersistenceProvider.init();
            readPreviousEpochsSpentAddresses();
        }
        catch (Exception e) {
            throw new SpentAddressesException("There is a problem with accessing stored spent addresses", e);
        }
        return this;
    }

    private void readPreviousEpochsSpentAddresses() throws SpentAddressesException {
        if (config.isTestnet()) {
            return;
        }

        for (String previousEpochsSpentAddressesFile : config.getPreviousEpochSpentAddressesFiles().split(" ")) {
            readSpentAddressesFromStream(
                    SpentAddressesProviderImpl.class.getResourceAsStream(previousEpochsSpentAddressesFile));
        }
    }

    private void readSpentAddressesFromStream(InputStream in) throws SpentAddressesException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                saveAddress(HashFactory.ADDRESS.create(line));
            }
        } catch (Exception e) {
            throw new SpentAddressesException("Failed to read or save spent address", e);
        }
    }

    @Override
    public boolean containsAddress(Hash addressHash) throws SpentAddressesException {
        try {
            return ((SpentAddress) rocksDBPersistenceProvider.get(SpentAddress.class, addressHash)).exists();
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void saveAddress(Hash addressHash) throws SpentAddressesException {
        try {
            rocksDBPersistenceProvider.save(new SpentAddress(), addressHash);
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void saveAddressesBatch(Collection<Hash> addressHash) throws SpentAddressesException {
        try {
            // Its bytes are always new byte[0], therefore identical in storage
            SpentAddress spentAddressModel = new SpentAddress();

            rocksDBPersistenceProvider.saveBatch(addressHash
                    .stream()
                    .map(address -> new Pair<Indexable, Persistable>(address, spentAddressModel))
                    .collect(Collectors.toList())
            );
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }
}
