package net.helix.pendulum.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.helix.pendulum.controllers.BundleViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.event.*;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.StateDiff;
import net.helix.pendulum.model.persistables.*;
import net.helix.pendulum.utils.Pair;
import net.helix.pendulum.zmq.MessageQProvider;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/3/17 for iri.
 */
public class Tangle implements PendulumEventListener {
    private static final Logger log = LoggerFactory.getLogger(Tangle.class);

    public static final Map<String, Class<? extends Persistable>> COLUMN_FAMILIES =
            new LinkedHashMap<String, Class<? extends Persistable>>() {{
                put("transaction", Transaction.class);
                put("round", Round.class);
                put("stateDiff", StateDiff.class);
                put("address", Address.class);
                put("approvee", Approvee.class);
                put("bundle", Bundle.class);
                put("bundleNonce", BundleNonce.class);
                put("tag", Tag.class);
                put("validator", Validator.class);
            }};

    public static final Map.Entry<String, Class<? extends Persistable>> METADATA_COLUMN_FAMILY =
            new AbstractMap.SimpleImmutableEntry<>("transaction-metadata", Transaction.class);

    private final List<PersistenceProvider> persistenceProviders = new ArrayList<>();
    private final List<MessageQProvider> messageQProviders = new ArrayList<>();


    public void addPersistenceProvider(PersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    /**
     * Adds {@link net.helix.pendulum.zmq.MessageQProvider} that should be notified.
     * @param provider that should be notified.
     */
    public void addMessageQueueProvider(MessageQProvider provider) {
        this.messageQProviders.add(provider);
    }

    public void init() throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
            provider.init();
        }

        EventManager.get().subscribe(EventType.TX_STORED, this);
    }


    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        this.persistenceProviders.forEach(PersistenceProvider::shutdown);
        this.persistenceProviders.clear();
    }

    public Persistable load(Class<?> model, Indexable index) throws Exception {
        Persistable out = null;
        for(PersistenceProvider provider: this.persistenceProviders) {
            if((out = provider.get(model, index)) != null) {
                break;
            }
        }
        return out;
    }

    public Boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        boolean exists = false;
        for(PersistenceProvider provider: persistenceProviders) {
            if(exists) {
                provider.saveBatch(models);
            } else {
                exists = provider.saveBatch(models);
            }
        }
        return exists;
    }
    public Boolean save(Persistable model, Indexable index) throws Exception {
        boolean exists = false;
        for(PersistenceProvider provider: persistenceProviders) {
            if(exists) {
                provider.save(model, index);
            } else {
                exists = provider.save(model, index);
            }
        }
        return exists;
    }

    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.deleteBatch(models);
        }
    }

    public void delete(Class<?> model, Indexable index) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.delete(model, index);
        }
    }

    public Pair<Indexable, Persistable> getLatest(Class<?> model, Class<?> index) throws Exception {
        Pair<Indexable, Persistable> latest = null;
        for(PersistenceProvider provider: persistenceProviders) {
            if (latest == null) {
                latest = provider.latest(model, index);
            }
        }
        return latest;
    }

    public void update(Persistable model, Indexable index, String item) throws Exception {
        updatePersistenceProvider(model, index, item);
        updateMessageQueueProvider(model, index, item);
    }

    private void updatePersistenceProvider(Persistable model, Indexable index, String item) throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
            provider.update(model, index, item);
        }
    }

    private void updateMessageQueueProvider(Persistable model, Indexable index, String item) {
        for(MessageQProvider provider: this.messageQProviders) {
            provider.publishTransaction(model, index, item);
        }
    }

    /**
     * Notifies all registered {@link net.helix.pendulum.zmq.MessageQProvider} and publishes message to MessageQueue.
     *
     * @param message that can be formatted by {@link String#format(String, Object...)}
     * @param objects that should replace the placeholder in message.
     * @see net.helix.pendulum.zmq.MessageQProviderImpl#publish(String, Object...)
     * @see String#format(String, Object...)
     */
    public void publish(String message, Object... objects) {
        for(MessageQProvider provider: this.messageQProviders) {
            provider.publish(message, objects);
        }
    }

    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> referencedClass) throws Exception {
        Set<Indexable> output = null;
        for(PersistenceProvider provider: this.persistenceProviders) {
            output = provider.keysWithMissingReferences(modelClass, referencedClass);
            if(output != null && output.size() > 0) {
                break;
            }
        }
        return output;
    }

    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        Set<Indexable> output = null;
        for(PersistenceProvider provider: this.persistenceProviders) {
            output = provider.keysStartingWith(modelClass, value);
            if(output.size() != 0) {
                break;
            }
        }
        return output;
    }

    public <T extends Indexable> List<T> loadAllKeysFromTable(Class<? extends Persistable> modelClass,
                                                              Function<byte[], T> transformer) {
        List<byte[]> keys = null;
        for(PersistenceProvider provider: this.persistenceProviders) {
            if ((keys = provider.loadAllKeysFromTable(modelClass)) != null) {
                break;
            }
        }

        if (keys != null) {
            return keys.stream()
                    .map(transformer)
                    .collect(Collectors.toList());
        }
        return null;
    }

    public Boolean exists(Class<?> modelClass, Indexable hash) throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
            if (provider.exists(modelClass, hash)) {
                return true;
            }
        }
        return false;
    }

    public Boolean maybeHas(Class<?> model, Indexable index) throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
            if (provider.mayExist(model, index)) {
                return true;
            }
        }
        return false;
    }

    public Long getCount(Class<?> modelClass) throws Exception {
        long value = 0;
        for(PersistenceProvider provider: this.persistenceProviders) {
            if((value = provider.count(modelClass)) != 0) {
                break;
            }
        }
        return value;
    }

    public Persistable find(Class<?> model, byte[] key) throws Exception {
        Persistable out = null;
        for (PersistenceProvider provider : this.persistenceProviders) {
            if ((out = provider.seek(model, key)) != null) {
                break;
            }
        }
        return out;
    }

    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
        Pair<Indexable, Persistable> latest = null;
        for(PersistenceProvider provider: persistenceProviders) {
            if(latest == null) {
                latest = provider.next(model, index);
            }
        }
        return latest;
    }

    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
        Pair<Indexable, Persistable> latest = null;
        for(PersistenceProvider provider: persistenceProviders) {
            if(latest == null) {
                latest = provider.previous(model, index);
            }
        }
        return latest;
    }

    public Pair<Indexable, Persistable > getFirst(Class<?> model, Class<?> index) throws Exception {
        Pair<Indexable, Persistable> latest = null;
        for(PersistenceProvider provider: persistenceProviders) {
            if(latest == null) {
                latest = provider.first(model, index);
            }
        }
        return latest;
    }

    public void clearColumn(Class<?> column) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.clear(column);
        }
    }

    public void clearMetadata(Class<?> column) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.clearMetadata(column);
        }
    }


    @Override
    public void handle(EventType type, EventContext ctx) {
        switch (type) {
            case TX_STORED:
                TransactionViewModel txvm = ctx.get(Key.key("TX", TransactionViewModel.class));
                publishStoredTx(txvm);
                break;
        }
    }

    ////////////////////
    //  Methods to handle various events
    ///////////////
    private void publishStoredTx(TransactionViewModel txvm) {
        try {
            BundleViewModel receivedBundle = BundleViewModel.load(this, txvm.getBundleHash());
            if (txvm.lastIndex() == receivedBundle.size() - 1) {
                JsonArray preBundle = new JsonArray();
                JsonArray publishBundle = new JsonArray();
                String oracleTopic = null;

                for (Hash txHash : receivedBundle.getHashes()) {
                    TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(this, txHash);
                    JsonObject addressTopicJson = new JsonObject();
                    addressTopicJson.addProperty("tx_hash", transactionViewModel.getHash().toString());
                    addressTopicJson.addProperty("bundle_hash", transactionViewModel.getBundleHash().toString());
                    addressTopicJson.addProperty("signature", Hex.toHexString(transactionViewModel.getSignature()));
                    addressTopicJson.addProperty("bundle_index", transactionViewModel.getCurrentIndex());
                    preBundle.add(addressTopicJson);

                    if (transactionViewModel.getCurrentIndex() == 0) {
                        oracleTopic = transactionViewModel.getAddressHash().toString();
                    }
                }
                for (int i = preBundle.size()-1; i >= 0; i--) {
                    publishBundle.add(preBundle.get(i));
                }
                publish("%s %s",
                        "ORACLE_" + (oracleTopic != null ?
                                oracleTopic : txvm.getAddressHash().toString())
                        , publishBundle.toString());
            }
        } catch (Exception e) {
            log.error("Error publishing bundle.", e);
        }
    }
    /*
    public boolean merge(Persistable model, Indexable index) throws Exception {
        boolean exists = false;
        for(PersistenceProvider provider: persistenceProviders) {
            if(exists) {
                provider.save(model, index);
            } else {
                exists = provider.merge(model, index);
            }
        }
        return exists;
    }
    */
}
