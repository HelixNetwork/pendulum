package net.helix.pendulum.service.spentaddresses.impl;

import net.helix.pendulum.conf.ConsensusConfig;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import net.helix.pendulum.model.persistables.SpentAddress;
import net.helix.pendulum.service.spentaddresses.SpentAddressesException;
import net.helix.pendulum.service.spentaddresses.SpentAddressesProvider;
import net.helix.pendulum.storage.Indexable;
import net.helix.pendulum.storage.Persistable;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.storage.rocksdb.RocksDBPersistenceProvider;
import net.helix.pendulum.utils.Pair;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * Implementation of <tt>SpentAddressesProvider</tt>.
 * Addresses are saved/found on the {@link Tangle}.
 * The folder location is provided by {@link PendulumConfig#getLocalSnapshotsBasePath()}
 *
 */
public class SpentAddressesProviderImpl implements SpentAddressesProvider {

    //@VisibleForTesting
    public RocksDBPersistenceProvider rocksDBPersistenceProvider;

    private ConsensusConfig config;

//    private String hello = config.getSpentAddressesDbPath();
//    private String kitty = config.getSpentAddressesDbLogPath();

    /**
     * Creates a new instance of SpentAddressesProvider
     */
    public SpentAddressesProviderImpl() {
    }

    /**
     * Starts the SpentAddressesProvider by reading the previous spent addresses from files.
     *
     * @param config The snapshot configuration used for file location
     * @return the current instance
     * @throws SpentAddressesException if we failed to create a file at the designated location
     */
    public SpentAddressesProviderImpl init(ConsensusConfig config)
            throws SpentAddressesException {
        this.config = config;
        Map<String, Class<? extends Persistable>> columnFamilies = new HashMap<>();
        columnFamilies.put("spent-addresses", SpentAddress.class);
        this.rocksDBPersistenceProvider = new RocksDBPersistenceProvider(
                config.getSpentAddressesDbPath(), config.getSpentAddressesDbLogPath(),
                1000,
                columnFamilies,
                null);
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
