package net.helix.pendulum.service.cache.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import net.helix.pendulum.Pendulum;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.Merkle;
import net.helix.pendulum.event.*;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.cache.TangleCache;
import net.helix.pendulum.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Date: 2019-11-14
 * Author: zhelezov
 */
public class TangleCacheImpl implements TangleCache, PendulumEventListener {
    private static final Logger log = LoggerFactory.getLogger(TangleCacheImpl.class);

    private static final int MAX_TX_CACHE_SIZE_HASHES = 10000; // should be < 20MB
    private static final int MAX_MERKLE_CACHE_SIZE_HASHES = 10000;

    private Cache<Hash, TransactionViewModel> txCache;
    private Cache<Hash, List<Hash>> parentsCache;
    private Cache<Hash, List<Hash>> approversCache;

    private Cache<Hash, Hash[]> rootToLeaves;
    private Cache<Hash[], Hash> leavesToRoot;

    private Tangle tangle;

    public TangleCacheImpl() {
        Pendulum.ServiceRegistry.get().register(TangleCache.class, this);
        EventManager.get().subscribe(EventType.TX_UPDATED, this);
    }

    @Override
    public Pendulum.Initializable init() {
        this.tangle = Pendulum.ServiceRegistry.get().resolve(Tangle.class);

        if (tangle == null) {
            throw new IllegalArgumentException("Tangle reference cannot be null");
        }

        txCache = CacheBuilder.newBuilder().
                    maximumSize(MAX_TX_CACHE_SIZE_HASHES).
                    build();

        rootToLeaves = CacheBuilder.newBuilder().
                weigher((Weigher<Hash, Hash[]>) (hash, list) -> list.length).
                maximumWeight(MAX_MERKLE_CACHE_SIZE_HASHES).build();

        leavesToRoot = CacheBuilder.newBuilder().
                weigher((Weigher<Hash[], Hash>) (list, hash) -> list.length).
                maximumWeight(MAX_MERKLE_CACHE_SIZE_HASHES).build();

        return this;
    }

    @Override
    public TransactionViewModel getTxVM(Hash hash) {
        try {
            return TransactionViewModel.fromHash(tangle, hash);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
//        if (hash == null) {
//            throw new IllegalArgumentException("Transaction hash cannot be null");
//        }
//        try {
//            return txCache.get(hash, () -> TransactionViewModel.fromHash(tangle, hash));
//            //if (tvm != null && (tvm.getType() == FILLED_SLOT) && tvm.isSolid()) {
//            //    return tvm;
//            //}
//            // otherwise rely on the database
//            //txCache.invalidate(hash);
//            //return TransactionViewModel.fromHash(tangle, hash);
//        } catch (Throwable t) {
//            throw new RuntimeException(t);
//        }
    }

    @Override
    public List<Hash> fromMerkleRoot(Hash hash) {
        Hash[] toReturn = rootToLeaves.getIfPresent(hash);
        if (toReturn == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(toReturn));
    }

    @Override
    public Hash toMerkleRoot(Collection<Hash> hashes) {
        Hash[] leaves = hashes.toArray(new Hash[0]);
        Arrays.sort(leaves, Comparator.comparing(Object::toString));
        Hash root = null;
        try {
            root = leavesToRoot.get(leaves, () -> {
                List<Hash> list = new ArrayList<>();
                Collections.addAll(list, leaves);
                List<List<Hash>> merkleTree = Merkle.buildMerkleTree(list);
                return merkleTree.get(merkleTree.size()-1).get(0);
            });
            rootToLeaves.put(root, leaves);
        } catch (ExecutionException e) {
            log.error("Error calculating hash", e);
            throw new RuntimeException(e);
        }
        return root;
    }


    @Override
    public List<Hash> getBundle(Hash txHash) {
        return null;
    }

    @Override
    public List<Hash> milestoneBundle(Hash txHash) {
        return null;
    }

    @Override
    public List<Hash> approvees(Hash txHash) {
        return null;
    }

    public void invalidateTxHash(Hash hash) {
        txCache.invalidate(hash);
    }

    @Override
    public void handle(EventType type, EventContext ctx) {
        if (type == EventType.TX_UPDATED || type == EventType.TX_STORED || type == EventType.TX_DELETED) {
            Hash txHash = EventUtils.getTxHash(ctx);
            // invalidate so that subsequent calls will load the new value
            txCache.invalidate(txHash);
        }
    }
}
