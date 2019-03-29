package net.helix.sbx.storage;

import net.helix.sbx.controllers.TransactionViewModel;
import net.helix.sbx.model.Hash;
import net.helix.sbx.model.persistables.Transaction;
import net.helix.sbx.utils.Converter;
import net.helix.sbx.utils.Pair;
import net.helix.sbx.zmq.MessageQ;

import org.bouncycastle.util.encoders.Hex;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZmqPublishProvider implements PersistenceProvider {

    private static final Logger log = LoggerFactory.getLogger(ZmqPublishProvider.class);
    private final MessageQ messageQ;

    public ZmqPublishProvider( MessageQ messageQ ) {
        this.messageQ = messageQ;
    }

    @Override
    public void init() throws Exception {

    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void shutdown() {

    }

    @Override
    public boolean save(Persistable model, Indexable index) throws Exception {
        return false;
    }

    @Override
    public void delete(Class<?> model, Indexable index) throws Exception {

    }

    @Override
    public boolean update(Persistable model, Indexable index, String item) throws Exception {
        if(!(model instanceof Transaction)) {
            return false;
        }
        if(!item.contains("sender")) {
            return false;
        }

        Transaction transaction = ((Transaction) model);
        TransactionViewModel transactionViewModel = new TransactionViewModel(transaction, (Hash)index);

        publishTx(transactionViewModel);
        publishTxBytes(transactionViewModel);

        return true;
    }


    private void publishTx(TransactionViewModel transactionViewModel) {
        StringBuilder txStringBuilder = new StringBuilder(600);

        try {
            txStringBuilder.append("tx_hash ");
            txStringBuilder.append(transactionViewModel.getHash().hexString()); txStringBuilder.append("\n");
            txStringBuilder.append("tx_address ");
            txStringBuilder.append(transactionViewModel.getAddressHash().hexString()); txStringBuilder.append("\n");
            txStringBuilder.append("tx_msg ");
            txStringBuilder.append(Hex.toHexString(transactionViewModel.getSignature()));

            /** TODO: not needed currently
            txStringBuilder.append(transactionViewModel.getHash().hexString()); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getAddressHash().hexString()); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.value())); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getBundleNonceHash().hexString()); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.getTimestamp())); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.getCurrentIndex())); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.lastIndex())); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getBundleHash().hexString()); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getTrunkTransactionHash().hexString()); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getBranchTransactionHash().hexString()); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.getArrivalTime())); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getTagValue().hexString());
            */

            messageQ.publish(txStringBuilder.toString());
        } catch (Exception e) {
            log.error(txStringBuilder.toString());
            log.error("Error publishing tx to zmq.", e);
        }
    }

    private void publishTxBytes(TransactionViewModel transactionViewModel) {
        StringBuilder txBytesStringBuilder = new StringBuilder(TransactionViewModel.SIZE);

        try {
            txBytesStringBuilder.append("tx_bytes ");
            txBytesStringBuilder.append(Hex.toHexString(transactionViewModel.getBytes()));

            messageQ.publish(txBytesStringBuilder.toString());
        } catch (Exception e) {
            log.error(txBytesStringBuilder.toString());
            log.error("Error publishing tx_bytes to zmq.", e);
        }
    }

    @Override
    public boolean exists(Class<?> model, Indexable key) throws Exception {
        return false;
    }

    @Override
    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception {
        return null;
    }

    @Override
    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> otherClass) throws Exception {
        return null;
    }

    @Override
    public Persistable get(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    @Override
    public boolean mayExist(Class<?> model, Indexable index) throws Exception {
        return false;
    }

    @Override
    public long count(Class<?> model) throws Exception {
        return 0;
    }

    @Override
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        return null;
    }

    @Override
    public Persistable seek(Class<?> model, byte[] key) throws Exception {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) throws Exception {
        return null;
    }

    @Override
    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        return false;
    }

    @Override
    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception {

    }

    @Override
    public void clear(Class<?> column) throws Exception {

    }

    @Override
    public void clearMetadata(Class<?> column) throws Exception {

    }

    @Override
    public List<byte[]> loadAllKeysFromTable(Class<? extends Persistable> model) {
        return null;
    }
}