package net.helix.sbx;

import net.helix.sbx.controllers.TransactionViewModel;
import net.helix.sbx.model.Hash;
import net.helix.sbx.storage.Tangle;
//import net.helix.sbx.zmq.MessageQ;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by paul on 3/27/17.
 */

/**
* The Transaction Requester stores and manages requested transactions and milestoneTracker transactions.
*/
public class TransactionRequester {

   private static final Logger log = LoggerFactory.getLogger(TransactionRequester.class);
   //private final MessageQ messageQ;

   // set of milestones and transactions
   private final Set<Hash> milestoneTransactionsToRequest = new LinkedHashSet<>();
   private final Set<Hash> transactionsToRequest = new LinkedHashSet<>();

   public static final int MAX_TX_REQ_QUEUE_SIZE = 10000;

   private static volatile long lastTime = System.currentTimeMillis();

   private static double P_REMOVE_REQUEST;
   private static boolean initialized = false;
   private final SecureRandom random = new SecureRandom();

   // tangle
   private final Object syncObj = new Object();
   private final Tangle tangle;

   public TransactionRequester(Tangle tangle){//, MessageQ messageQ) {
       this.tangle = tangle;
       //this.messageQ = messageQ;
   }

   public void init(double p_REMOVE_REQUEST) {
       if(!initialized) {
           initialized = true;
           P_REMOVE_REQUEST = p_REMOVE_REQUEST;
       }
   }

   /**
   * Return requested transactions and requested milestones as array.
   * @return <code> Hash[] </code> requested transactions
   */
   public Hash[] getRequestedTransactions() {
       synchronized (syncObj) {
           return ArrayUtils.addAll(transactionsToRequest.stream().toArray(Hash[]::new),
                   milestoneTransactionsToRequest.stream().toArray(Hash[]::new));
       }
   }

   /**
   * Return number of requested transactions (+ requested milestones).
   * @return <code> int </code> number of requested transactions
   */
   public int numberOfTransactionsToRequest() {
       return transactionsToRequest.size() + milestoneTransactionsToRequest.size();
   }

   /**
   * Remove given transaction from requested transactions (or requested milestones).
   * @return <code> boolean </code> success
   */
   public boolean clearTransactionRequest(Hash hash) {
       synchronized (syncObj) {
           boolean milestone = milestoneTransactionsToRequest.remove(hash);
           boolean normal = transactionsToRequest.remove(hash);
           return normal || milestone;
       }
   }

   /**
   * Add transaction to requestes transactions (or requested milestones).
   * @param hash transaction crypto
   * @param milestone false for normal transactions, true for milestoneTracker transactions
   */
   public void requestTransaction(Hash hash, boolean milestone) throws Exception {
       if (!hash.equals(Hash.NULL_HASH) && !TransactionViewModel.exists(tangle, hash)) {
           synchronized (syncObj) {
               if(milestone) {
                   transactionsToRequest.remove(hash);
                   milestoneTransactionsToRequest.add(hash);
               } else {
                   if(!milestoneTransactionsToRequest.contains(hash) && !transactionsToRequestIsFull()) {
                       transactionsToRequest.add(hash);
                   }
               }
           }
       }
   }

   /**
   * Check if max transaction queue size is reached (10000 tx).
   * @return <code> boolean </code> full
   */
   private boolean transactionsToRequestIsFull() {
       return transactionsToRequest.size() >= TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
   }

   /**
   * Checks if requested transactions already exists in tangle
   * @return <code> Hash </code> ?
   */
   public Hash transactionToRequest(boolean milestone) throws Exception {
       final long beginningTime = System.currentTimeMillis();
       Hash hash = null;
       Set<Hash> requestSet;
       if(milestone) {
            requestSet = milestoneTransactionsToRequest;
            if(requestSet.size() == 0) {
                requestSet = transactionsToRequest;
            }
       } else {
           requestSet = transactionsToRequest;
           if(requestSet.size() == 0) {
               requestSet = milestoneTransactionsToRequest;
           }
       }
       synchronized (syncObj) {
           while (requestSet.size() != 0) {
               Iterator<Hash> iterator = requestSet.iterator();
               hash = iterator.next();
               iterator.remove();
               if (TransactionViewModel.exists(tangle, hash)) {
                   log.info("Removed existing tx from request list: " + hash);
                   //messageQ.publish("rtl %s", crypto);
               } else {
                   if (!transactionsToRequestIsFull()) {
                       requestSet.add(hash);
                   }
                   break;
               }
           }
       }

       if(random.nextDouble() < P_REMOVE_REQUEST && !requestSet.equals(milestoneTransactionsToRequest)) {
           synchronized (syncObj) {
               transactionsToRequest.remove(hash);
           }
       }

       long now = System.currentTimeMillis();
       if ((now - lastTime) > 10000L) {
           lastTime = now;
           //log.info("Transactions to request = {}", numberOfTransactionsToRequest() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime) + " ms ). " );
       }
       return hash;
   }

}
