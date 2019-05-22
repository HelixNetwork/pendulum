package net.helix.hlx.zmq;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.persistables.Transaction;
import net.helix.hlx.storage.Indexable;
import net.helix.hlx.storage.Persistable;

/**
 * Publish messages to the MessageQueue.
 */
public interface MessageQProvider {
    /**
     * Publishes the message to the MessageQueue.
     *
     * @param message that can be formatted by {@link String#format(String, Object...)}
     * @param objects that should replace the placeholder in message.
     * @see String#format(String, Object...)
     */
    void publish(String message, Object... objects);

    /**
     * Publishes the transaction details to the MessageQueue.
     *
     * @param model with Transaction details send to the MessageQueue.
     * @param index {@link Hash} identifier of the {@link Transaction} set
     * @param item identifying the purpose of the update
     * @return true when message was send to the MessageQueue
     */
    boolean publishTransaction(Persistable model, Indexable index, String item);

    /**
     * Shutdown the MessageQueue.
     */
    void shutdown();
}
