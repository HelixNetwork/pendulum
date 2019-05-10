package net.helix.sbx.zmq;

import net.helix.sbx.conf.ZMQConfig;
import net.helix.sbx.controllers.TransactionViewModel;
import net.helix.sbx.model.Hash;
import net.helix.sbx.model.persistables.Transaction;
import net.helix.sbx.storage.Indexable;
import net.helix.sbx.storage.Persistable;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use <a href="http://zeromq.org/" target="_top">zeromq</a> to create a MessageQueue that publishes messages.
 */
public class MessageQProviderImpl implements MessageQProvider {

    private static final Logger log = LoggerFactory.getLogger(MessageQProviderImpl.class);
    private final MessageQ messageQ;

    /**
     * Factory method to create a new ZmqMessageQueue with the given configuration.
     *
     * @param configuration with the zmq properties used to create MessageQueue
     */
    public MessageQProviderImpl(ZMQConfig configuration ) {
        this.messageQ = MessageQ.createWith(configuration);
    }

    @Override
    public boolean publishTransaction(Persistable model, Indexable index, String item) {
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

    /**
     * Publishes the message to the MessageQueue.
     *
     * @param message that can be formatted by {@link String#format(String, Object...)}
     * @param objects that should replace the placeholder in message.
     * @see String#format(String, Object...)
     */
    @Override
    public void publish(String message, Object... objects) {
        this.messageQ.publish(message, objects);
    }

    @Override
    public void shutdown() {
        this.messageQ.shutdown();
    }

}
