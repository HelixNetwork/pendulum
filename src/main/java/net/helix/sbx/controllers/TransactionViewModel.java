package net.helix.sbx.controllers;

import java.util.*;
import net.helix.sbx.model.*;
import net.helix.sbx.model.persistables.*;
import net.helix.sbx.service.snapshot.Snapshot;
import net.helix.sbx.storage.Indexable;
import net.helix.sbx.storage.Persistable;
import net.helix.sbx.storage.Tangle;
import net.helix.sbx.utils.Converter;
import net.helix.sbx.utils.Pair;
import net.helix.sbx.utils.Serializer;
import org.bouncycastle.util.encoders.Hex;


/**
* The TransactionViewModel class contains the AddressViewModel, the ApproveeViewModel, the TransactionViewModel
* of trunk and branch and the hash of a transaction.
* The size and offset of the transaction attributes and the supply are also defined here.
*/
public class TransactionViewModel {

    private final Transaction transaction;

    public static final int SIZE = 768;

    public static final long SUPPLY = 4292493394837504L;

    public static final int SIGNATURE_MESSAGE_FRAGMENT_OFFSET = 0;
    public static final int SIGNATURE_MESSAGE_FRAGMENT_SIZE = 512;
    public static final int ADDRESS_OFFSET = SIGNATURE_MESSAGE_FRAGMENT_OFFSET + SIGNATURE_MESSAGE_FRAGMENT_SIZE;
    public static final int ADDRESS_SIZE = 32;
    public static final int VALUE_OFFSET = ADDRESS_OFFSET + ADDRESS_SIZE, VALUE_SIZE = 8;
    public static final int VALUE_USABLE_SIZE = 8;
    public static final int BUNDLE_NONCE_OFFSET = VALUE_OFFSET + VALUE_SIZE;
    public static final int BUNDLE_NONCE_SIZE = 32;
    public static final int TIMESTAMP_OFFSET = BUNDLE_NONCE_OFFSET  + BUNDLE_NONCE_SIZE;
    public static final int TIMESTAMP_SIZE = 8;
    public static final int CURRENT_INDEX_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
    public static final int CURRENT_INDEX_SIZE = 8;
    public static final int LAST_INDEX_OFFSET = CURRENT_INDEX_OFFSET + CURRENT_INDEX_SIZE;
    public static final int LAST_INDEX_SIZE = 8;
    public static final int BUNDLE_OFFSET = LAST_INDEX_OFFSET + LAST_INDEX_SIZE;
    public static final int BUNDLE_SIZE = 32;
    public static final int TRUNK_TRANSACTION_OFFSET = BUNDLE_OFFSET + BUNDLE_SIZE;
    public static final int TRUNK_TRANSACTION_SIZE = 32;
    public static final int BRANCH_TRANSACTION_OFFSET = TRUNK_TRANSACTION_OFFSET + TRUNK_TRANSACTION_SIZE;
    public static final int BRANCH_TRANSACTION_SIZE = 32;
    public static final int TAG_OFFSET = BRANCH_TRANSACTION_OFFSET + BRANCH_TRANSACTION_SIZE;
    public static final int TAG_SIZE = 8;
    public static final int ATTACHMENT_TIMESTAMP_OFFSET = TAG_OFFSET + TAG_SIZE;
    public static final int ATTACHMENT_TIMESTAMP_SIZE = 8;
    public static final int ATTACHMENT_TIMESTAMP_LOWER_BOUND_OFFSET = ATTACHMENT_TIMESTAMP_OFFSET + ATTACHMENT_TIMESTAMP_SIZE;
    public static final int ATTACHMENT_TIMESTAMP_LOWER_BOUND_SIZE = 8;
    public static final int ATTACHMENT_TIMESTAMP_UPPER_BOUND_OFFSET = ATTACHMENT_TIMESTAMP_LOWER_BOUND_OFFSET + ATTACHMENT_TIMESTAMP_LOWER_BOUND_SIZE;
    public static final int ATTACHMENT_TIMESTAMP_UPPER_BOUND_SIZE = 8;
    public static final int NONCE_OFFSET = ATTACHMENT_TIMESTAMP_UPPER_BOUND_OFFSET + ATTACHMENT_TIMESTAMP_UPPER_BOUND_SIZE;
    public static final int NONCE_SIZE = 8;


    public static final int ESSENCE_OFFSET = ADDRESS_OFFSET, ESSENCE_SIZE = ADDRESS_SIZE + VALUE_SIZE + BUNDLE_NONCE_SIZE + TIMESTAMP_SIZE + CURRENT_INDEX_SIZE + LAST_INDEX_SIZE;


    private AddressViewModel address;
    private ApproveeViewModel approovers;
    private TransactionViewModel trunk;
    private TransactionViewModel branch;
    private final Hash hash;


    public final static int GROUP = 0; // transactions GROUP means that's it's a non-leaf node (leafs store transaction value)
    public final static int PREFILLED_SLOT = 1; // means that we know only hash of the tx, the rest is unknown yet: only another tx references that hash
    public final static int FILLED_SLOT = -1; //  knows the hash only coz another tx references that hash

    //private int[] trits;
    public int weightMagnitude;

    /**
    * Write transactions meta data into the database.
    * @param tangle
    * @param transactionViewModel
    */
    public static void fillMetadata(Tangle tangle, TransactionViewModel transactionViewModel) throws Exception {
        if (Hash.NULL_HASH.equals(transactionViewModel.getHash())) {
            return;
        }
        if(transactionViewModel.getType() == FILLED_SLOT && !transactionViewModel.transaction.parsed) {
            tangle.saveBatch(transactionViewModel.getMetadataSaveBatch());
        }
    }

    /**
    * Find transaction in the database. Uses @see #Tangle.find(Class<?>, byte[]), which returns null if the transaction don't exists.
    * @param tangle
    * @param hash transaction hash
    * @return <code>TransactionViewModel</code> of the transaction
    */
    public static TransactionViewModel find(Tangle tangle, byte[] hash) throws Exception {
        Transaction tx = (Transaction) tangle.find(Transaction.class, hash);
        TransactionViewModel transactionViewModel = new TransactionViewModel(tx, HashFactory.TRANSACTION.create(hash));
        fillMetadata(tangle, transactionViewModel);
        return transactionViewModel;
    }

    /**
    * Get TransactionViewModel of a given transaction hash. Uses @see #Tangle.load(Class<?>, Indexable)
    * @param tangle
    * @param hash transaction hash
    * @return <code>TransactionViewModel</code> of the transaction
    */
    public static TransactionViewModel fromHash(Tangle tangle, final Hash hash) throws Exception {
        TransactionViewModel transactionViewModel = new TransactionViewModel((Transaction) tangle.load(Transaction.class, hash), hash);
        fillMetadata(tangle, transactionViewModel);
        return transactionViewModel;
    }

    /**
    * Get TransactionViewModel of a given transaction hash. Uses @see #Tangle.maybeHas(Class<?>, Indexable),
    * which checks the possible existence of an entry in the database.
    * @param tangle
    * @param hash transaction hash
    * @return <code>boolean</code> existence
    */
    public static boolean mightExist(Tangle tangle, Hash hash) throws Exception {
        return tangle.maybeHas(Transaction.class, hash);
    }

    /**
    * Constructor with transaction model and transaction hash.
    * @param transaction transaction model
    * @param hash transaction hash
    */
    public TransactionViewModel(final Transaction transaction, final Hash hash) {
        this.transaction = transaction == null || transaction.bytes == null ? new Transaction(): transaction;
        this.hash = hash == null? Hash.NULL_HASH: hash;

        // depends on trailing or leading
        // weightMagnitude = this.hash.trailingZeros();
        weightMagnitude = this.hash.leadingZeros();
    }

    /**
    * Constructor with transaction bytes and transaction hash.
    * @param bytes transaction bytes
    * @param hash transaction hash
    */
    public TransactionViewModel(final byte[] bytes, Hash hash) throws RuntimeException {
        transaction = new Transaction();
        transaction.bytes = new byte[SIZE];
        System.arraycopy(bytes, 0, transaction.bytes, 0, SIZE);
        this.hash = hash;

        // depends on trailing or leading
        // weightMagnitude = this.hash.trailingZeros();
        weightMagnitude = this.hash.leadingZeros();
        transaction.type = FILLED_SLOT;
    }

    /**
    * Get the number of transactins in the database.
    * @param tangle
    * @return <class> int </class> number of transactions
    */
    public static int getNumberOfStoredTransactions(Tangle tangle) throws Exception {
        return tangle.getCount(Transaction.class).intValue();
    }

    /**
     * This method updates the metadata contained in the {@link Transaction} object, and updates the object in the
     * database. First, all the most recent {@link Hash} identifiers are fetched to make sure the object's metadata is
     * up to date. Then it checks if the current {@link TransactionHash} is null. If it is, then the method immediately
     * returns false, and if not, it attempts to update the {@link Transaction} object and the referencing {@link Hash}
     * identifier in the database.
     *
     * @param tangle The tangle reference for the database
     * @param initialSnapshot snapshot that acts as genesis
     * @param item The string identifying the purpose of the update
     * @return True if the update was successful, False if it failed
     * @throws Exception Thrown if any of the metadata fails to fetch, or if the database update fails
     */
    public boolean update(Tangle tangle, Snapshot initialSnapshot, String item) throws Exception {
        getAddressHash();
        getTrunkTransactionHash();
        getBranchTransactionHash();
        getBundleHash();
        getTagValue();
        getBundleNonceHash();
        setAttachmentData();
        setMetadata();
        /*if(hash.equals(Hash.NULL_HASH)) {
            return false;
        }oldimpl*/
        if (initialSnapshot.hasSolidEntryPoint(hash)) {
            return false;
        }
        return tangle.update(transaction, hash, item);
    }

    /**
     * Retrieves the {@link TransactionViewModel} for the branch {@link Transaction} object referenced by this
     * {@link TransactionViewModel}. If the controller doesn't already exist, a new one is created from the branch
     * transaction {@link Hash} identifier.
     *
     * @param tangle The tangle reference for the database.
     * @return The branch transaction {@link TransactionViewModel}
     * @throws Exception Thrown if no branch is found when creating the branch {@link TransactionViewModel}
     */
    public TransactionViewModel getBranchTransaction(Tangle tangle) throws Exception {
        if(branch == null) {
            branch = TransactionViewModel.fromHash(tangle, getBranchTransactionHash());
        }
        return branch;
    }

    /**
     * Retrieves the {@link TransactionViewModel} for the trunk {@link Transaction} object referenced by this
     * {@link TransactionViewModel}. If the controller doesn't already exist, a new one is created from the trunk
     * transaction {@link Hash} identifier.
     *
     * @param tangle The tangle reference for the database.
     * @return The trunk transaction {@link TransactionViewModel}
     * @throws Exception Thrown if no trunk is found when creating the trunk {@link TransactionViewModel}
     */
    public TransactionViewModel getTrunkTransaction(Tangle tangle) throws Exception {
        if(trunk == null) {
            trunk = TransactionViewModel.fromHash(tangle, getTrunkTransactionHash());
        }
        return trunk;
    }

    /**
     * Deletes the {@link Transaction} object from the database
     *
     * @param tangle The tangle reference for the database
     * @throws Exception Thrown if there is an error removing the object
     */
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Transaction.class, hash);
    }

    /**
     * Stores the {@link Transaction} object to the tangle, including the metadata and indexing based on {@link Bundle},
     * {@link Address}, {@link Tag}, {@link #trunk} and {@link #branch}.
     *
     * @return The list of {@link Hash} objects indexed by the {@link TransactionHash} identifier. Returns False if
     * there is a problem populating the list.
     */
    public List<Pair<Indexable, Persistable>> getMetadataSaveBatch() throws Exception {
        List<Pair<Indexable, Persistable>> hashesList = new ArrayList<>();
        hashesList.add(new Pair<>(getAddressHash(), new Address(hash)));
        hashesList.add(new Pair<>(getBundleHash(), new Bundle(hash)));
        hashesList.add(new Pair<>(getBranchTransactionHash(), new Approvee(hash)));
        hashesList.add(new Pair<>(getTrunkTransactionHash(), new Approvee(hash)));
        hashesList.add(new Pair<>(getBundleNonceHash(), new BundleNonce(hash)));
        hashesList.add(new Pair<>(getTagValue(), new Tag(hash)));
        setAttachmentData();
        setMetadata();
        return hashesList;
    }

    /**
     * Fetches a list of all {@link Transaction} component and {@link Hash} identifier pairs from the stored metadata.
     * The method then ensures that the {@link Transaction#bytes} are present before adding the {@link Transaction} and
     * {@link Hash} identifier to the already compiled list of {@link Transaction} components.
     *
     * @return A complete list of all {@link Transaction} component objects paired with their {@link Hash} identifiers
     * @throws Exception Thrown if the metadata fails to fetch, or if the bytes are not retrieved correctly
     */
    public List<Pair<Indexable, Persistable>> getSaveBatch() throws Exception {
        List<Pair<Indexable, Persistable>> hashesList = new ArrayList<>();
        hashesList.addAll(getMetadataSaveBatch());
        getBytes();
        hashesList.add(new Pair<>(hash, transaction));
        return hashesList;
    }

    /**
    * Get first transaction entry from database.
    * @param tangle
    * @return <code> TransactionViewModel </code>
    */
    public static TransactionViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> transactionPair = tangle.getFirst(Transaction.class, Hash.class);
        if(transactionPair != null && transactionPair.hi != null) {
            return new TransactionViewModel((Transaction) transactionPair.hi, (Hash) transactionPair.low);
        }
        return null;
    }

    /**
     * Fetches the next indexed persistable {@link Transaction} object from the database and generates a new
     * {@link TransactionViewModel} from it. If no objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database.
     * @return The new {@link TransactionViewModel}.
     * @throws Exception Thrown if the database fails to return a next object.
     */
    public TransactionViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> transactionPair = tangle.next(Transaction.class, hash);
        if(transactionPair != null && transactionPair.hi != null) {
            return new TransactionViewModel((Transaction) transactionPair.hi, (Hash) transactionPair.low);
        }
        return null;
    }

    /**
     * This method fetches the saved batch of metadata and orders them into a list of {@link Hash} objects and
     * {@link Hash} identifier pairs. If the {@link Hash} identifier of the {@link Transaction} is null, or the database
     * already contains the {@link Transaction}, then the method returns False. Otherwise, the method tries to store the
     * {@link Transaction} batch into the database.
     *
     * @param tangle The tangle reference for the database.
     * @param initialSnapshot snapshot that acts as genesis
     * @return True if the {@link Transaction} is stored, False if not.
     * @throws Exception Thrown if there is an error fetching the batch or storing in the database.
     */
    public boolean store(Tangle tangle, Snapshot initialSnapshot) throws Exception {
        if (initialSnapshot.hasSolidEntryPoint(hash) || exists(tangle, hash)) {
            return false;
        }

        List<Pair<Indexable, Persistable>> batch = getSaveBatch();
        if (exists(tangle, hash)) {
            return false;
        }
        return tangle.saveBatch(batch);
    }

    /**
     * Gets the {@link ApproveeViewModel} of a {@link Transaction}. If the current {@link ApproveeViewModel} is null, a
     * new one is created using the transaction {@link Hash} identifier.
     *
     * An {@link Approvee} is a transaction in the tangle that references, and therefore approves, this transaction
     * directly.
     *
     * @param tangle The tangle reference for the database
     * @return The {@link ApproveeViewModel}
     * @throws Exception Thrown if there is a failure to create a controller from the transaction hash
     */
    public ApproveeViewModel getApprovers(Tangle tangle) throws Exception {
        if(approovers == null) {
            approovers = ApproveeViewModel.load(tangle, hash);
        }
        return approovers;
    }

    /**
     * Gets the {@link Transaction#type}. The type can be one of 3:
     * <ul>
     * <li>PREFILLED_SLOT: 1</li>
     * <li>FILLED_SLOT: -1</li>
     * <li>GROUP: 0</li>
     * </ul>
     *
     * @return The current type of the transaction.
     */
    public final int getType() {
        return transaction.type;
    }

    /**
     * Sets the {@link Transaction#arrivalTime}.
     *
     * @param time The time to be set in the {@link Transaction}
     */
    public void setArrivalTime(long time) {
        transaction.arrivalTime = time;
    }

    public long getArrivalTime() {
        return transaction.arrivalTime;
    }

    /**
     * Gets the stored {@link Transaction#bytes}. If the {@link Transaction#bytes} are null, a new byte array is created
     * and stored from the bytes. If the bytes are also null, then a null byte array is returned.
     *
     * @return The stored {@link Transaction#bytes} array
     */
    public byte[] getBytes() {
        if(transaction.bytes == null || transaction.bytes.length != SIZE) {
            transaction.bytes = new byte[SIZE];
            /*if(trits != null) {
                Converter.bytes(trits(), 0, transaction.bytes, 0, trits().length);
            }*/ //todo validation txvm
        }
        return transaction.bytes;
    }

    public Hash getHash() {
        return hash;
    }

    /**
     * Gets the {@link AddressViewModel} associated with this {@link Transaction}.
     *
     * @param tangle The tangle reference for the database.
     * @return The {@link AddressViewModel} of the {@link Transaction}.
     * @throws Exception If the address cannot be found in the database, an exception is thrown.
     */
    public AddressViewModel getAddress(Tangle tangle) throws Exception {
        if(address == null) {
            address = AddressViewModel.load(tangle, getAddressHash());
        }
        return address;
    }

    /**
     * Gets the {@link TagViewModel} associated with this {@link Transaction}.
     *
     * @param tangle The tangle reference for the database.
     * @return The {@link TagViewModel} of the {@link Transaction}.
     * @throws Exception If the address cannot be found in the database, an exception is thrown.
     */
    public TagViewModel getTag(Tangle tangle) throws Exception {
        return TagViewModel.load(tangle, getTagValue());
    }

    /**
     * Gets the {@link AddressHash} identifier of a {@link Transaction}.
     *
     * @return The {@link AddressHash} identifier.
     */
    public Hash getAddressHash() {
        if(transaction.address == null) {
            transaction.address =  HashFactory.ADDRESS.create(getBytes(), ADDRESS_OFFSET);
        }
        return transaction.address;
    }

    /**
     * Gets the {@link BundleNonceHash} identifier of a {@link Transaction}.
     *
     * @return The {@link BundleNonceHash} identifier.
     */
    public Hash getBundleNonceHash() {
        if(transaction.bundleNonce == null) {
            transaction.bundleNonce = HashFactory.BUNDLENONCE.create(getBytes(), BUNDLE_NONCE_OFFSET);
        }
        return transaction.bundleNonce;
    }

    /**
     * Gets the {@link BundleHash} identifier of a {@link Transaction}.
     *
     * @return The {@link BundleHash} identifier.
     */
    public Hash getBundleHash() {
        if(transaction.bundle == null) {
            transaction.bundle = HashFactory.BUNDLE.create(getBytes(), BUNDLE_OFFSET);
        }
        return transaction.bundle;
    }

    /**
     * Gets the trunk {@link TransactionHash} identifier of a {@link Transaction}.
     *
     * @return The trunk {@link TransactionHash} identifier.
     */
    public Hash getTrunkTransactionHash() {
        if(transaction.trunk == null) {
            transaction.trunk = HashFactory.TRANSACTION.create(getBytes(), TRUNK_TRANSACTION_OFFSET);
        }
        return transaction.trunk;
    }

    /**
     * Gets the branch {@link TransactionHash} identifier of a {@link Transaction}.
     *
     * @return The branch {@link TransactionHash} identifier.
     */
    public Hash getBranchTransactionHash() {
        if(transaction.branch == null) {
            transaction.branch = HashFactory.TRANSACTION.create(getBytes(), BRANCH_TRANSACTION_OFFSET);
        }
        return transaction.branch;
    }

    /**
     * Gets the {@link TagHash} identifier of a {@link Transaction}.
     *
     * @return The {@link TagHash} identifier.
     */
    public Hash getTagValue() {
        if(transaction.tag == null) {
            transaction.tag = HashFactory.TAG.create(getBytes(), TAG_OFFSET);
        }
        return transaction.tag;
    }

    /**
     * Gets the {@link Transaction#attachmentTimestamp}. The <tt>Attachment Timestapm</tt> is used to show when a
     * transaction has been attached to the database.
     *
     * @return The {@link Transaction#attachmentTimestamp}
     */
    public long getAttachmentTimestamp() { return transaction.attachmentTimestamp; }

    /**
     * Gets the {@link Transaction#attachmentTimestampLowerBound}. The <tt>Attachment Timestamp Lower Bound</tt> is the
     * earliest timestamp a transaction can have.
     *
     * @return The {@link Transaction#attachmentTimestampLowerBound}
     */
    public long getAttachmentTimestampLowerBound() {
        return transaction.attachmentTimestampLowerBound;
    }

    /**
     * Gets the {@link Transaction#attachmentTimestampUpperBound}. The <tt>Attachment Timestamp Upper Bound</tt> is the
     * maximum timestamp a transaction can have.
     *
     * @return The {@link Transaction#attachmentTimestampUpperBound}
     */
    public long getAttachmentTimestampUpperBound() {
        return transaction.attachmentTimestampUpperBound;
    }


    public long value() {
        return transaction.value;
    }

    /**
     * Updates the {@link Transaction#validity} in the database.
     *
     * The validity can be one of three states: <tt>1: Valid; -1: Invalid; 0: Unknown</tt>
     *
     * @param tangle The tangle reference for the database
     * @param initialSnapshot snapshot that acts as genesis
     * @param validity The state of validity that the {@link Transaction} will be updated to
     * @throws Exception Thrown if there is an error with the update
     */
    public void setValidity(Tangle tangle, Snapshot initialSnapshot, int validity) throws Exception {
        if(transaction.validity != validity) {
            transaction.validity = validity;
            update(tangle, initialSnapshot, "validity");
        }
    }

    public int getValidity() {
        return transaction.validity;
    }

    public long getCurrentIndex() {
        return transaction.currentIndex;
    }

    // trits -> bytes
    public byte[] getSignature() {
        return Arrays.copyOfRange(getBytes(), SIGNATURE_MESSAGE_FRAGMENT_OFFSET, SIGNATURE_MESSAGE_FRAGMENT_SIZE);
    }

    public long getTimestamp() {
        return transaction.timestamp;
    }

    public byte[] getNonce() {
        return Arrays.copyOfRange(getBytes(), NONCE_OFFSET, NONCE_OFFSET+NONCE_SIZE);
    }

    public long lastIndex() {
        return transaction.lastIndex;
    }

    public void setAttachmentData() {
        getTagValue();
        transaction.attachmentTimestamp = Converter.bytesToLong(getBytes(), ATTACHMENT_TIMESTAMP_OFFSET);
        transaction.attachmentTimestampLowerBound = Converter.bytesToLong(getBytes(), ATTACHMENT_TIMESTAMP_LOWER_BOUND_OFFSET);
        transaction.attachmentTimestampUpperBound = Converter.bytesToLong(getBytes(), ATTACHMENT_TIMESTAMP_UPPER_BOUND_OFFSET);
    }

    public void setMetadata() {
        transaction.value = Converter.bytesToLong(getBytes(), VALUE_OFFSET);
        transaction.timestamp = Converter.bytesToLong(getBytes(), TIMESTAMP_OFFSET);
        //if (transaction.timestamp > 1262304000000L ) transaction.timestamp /= 1000L;  // if > 01.01.2010 in milliseconds
        transaction.currentIndex = Converter.bytesToLong(getBytes(), CURRENT_INDEX_OFFSET);
        transaction.lastIndex = Converter.bytesToLong(getBytes(), LAST_INDEX_OFFSET);
        transaction.type = transaction.bytes == null ? TransactionViewModel.PREFILLED_SLOT : TransactionViewModel.FILLED_SLOT;
    }

    public static boolean exists(Tangle tangle, Hash hash) throws Exception {
        return tangle.exists(Transaction.class, hash);
    }

    public static Set<Indexable> getMissingTransactions(Tangle tangle) throws Exception {
        return tangle.keysWithMissingReferences(Approvee.class, Transaction.class);
    }

    /**
    * Update heights and solid state of given transactions.
    * @param tangle
    * @param analyzedHashes set of transaction hashes
    */
    public static void updateSolidTransactions(Tangle tangle, Snapshot initialSnapshot, final Set<Hash> analyzedHashes) throws Exception {
        Iterator<Hash> hashIterator = analyzedHashes.iterator();
        TransactionViewModel transactionViewModel;
        while(hashIterator.hasNext()) {
            transactionViewModel = TransactionViewModel.fromHash(tangle, hashIterator.next());

            transactionViewModel.updateHeights(tangle, initialSnapshot);

            if(!transactionViewModel.isSolid()) {
                transactionViewModel.updateSolid(true);
                transactionViewModel.update(tangle, initialSnapshot,  "solid|height");
            }
        }
    }

    /**
    * Update solid state.
    * @param solid true/false
    * @return <code> boolean </code> update neccessary
    */
    public boolean updateSolid(boolean solid) throws Exception {
        if(solid != transaction.solid) {
            transaction.solid = solid;
            return true;
        }
        return false;
    }

    public boolean isSolid() {
        return transaction.solid;
    }

    public int snapshotIndex() {
        return transaction.snapshot;
    }

    /**
    * Set new snapshot index.
    * @param tangle
    * @param index snapshot index
    * @return <code> ApproveeViewModel </code> success
    */
    public void setSnapshot(Tangle tangle, Snapshot initialSnapshot, final int index) throws Exception {
        if ( index != transaction.snapshot ) {
            transaction.snapshot = index;
            update(tangle, initialSnapshot, "snapshot");
        }
    }

    /**
     * This method sets the {@link Transaction#milestone} flag.
     *
     * It gets automatically called by the {@link net.helix.sbx.service.milestone.LatestMilestoneTracker} and marks transactions that represent a
     * milestone accordingly. It first checks if the {@link Transaction#milestone} flag has changed and if so, it issues
     * a database update.
     *
     * @param tangle Tangle instance which acts as a database interface <<<<<<< HEAD
     * @param isMilestone True if the {@link Transaction} is a milestone and False if not
     * @throws Exception Thrown if there is an error while saving the changes to the database =======
     * @param initialSnapshot the snapshot representing the starting point of our ledger
     * @param isMilestone true if the transaction is a milestone and false otherwise
     * @throws Exception if something goes wrong while saving the changes to the database >>>>>>> release-v1.5.6
     */
    public void isMilestone(Tangle tangle, Snapshot initialSnapshot, final boolean isMilestone) throws Exception {
        if (isMilestone != transaction.milestone) {
            transaction.milestone = isMilestone;
            update(tangle, initialSnapshot, "milestone");
        }
    }

    /**
     * This method gets the {@link Transaction#milestone}.
     *
     * The {@link Transaction#milestone} flag indicates if the {@link Transaction} is a coordinator issued milestone. It
     * allows us to differentiate the two types of transactions (normal transactions / milestones) very fast and
     * efficiently without issuing further database queries or even full verifications of the signature. If it is set to
     * true one can for example use the snapshotIndex() method to retrieve the corresponding {@link MilestoneViewModel}
     * object.
     *
     * @return true if the {@link Transaction} is a milestone and false otherwise
     */
    public boolean isMilestone() {
        return transaction.milestone;
    }

    /** @return The current {@link Transaction#height} */
    public long getHeight() {
        return transaction.height;
    }

    private void updateHeight(long height) throws Exception {
        transaction.height = height;
    }

    /**
    * Update heights of the transaction. The height is the longest oriented path to the genesis.
    * @param tangle
    */
    public void updateHeights(Tangle tangle, Snapshot initialSnapshot) throws Exception {
        TransactionViewModel transactionVM = this, trunk = this.getTrunkTransaction(tangle);
        Stack<Hash> transactionViewModels = new Stack<>();
        // transaction wird dem stack hinzugefügt
        transactionViewModels.push(transactionVM.getHash());
        /*
        * Solange man nicht bei der wurzel oder bei einem nullhash angekommen ist,
        * wird der weg im tangle über die trunk transactions bis zur wurzel gegangen
        * und die transaction hashes in einem stack gespeichert
        */
        while(trunk.getHeight() == 0 && trunk.getType() != PREFILLED_SLOT && !trunk.getHash().equals(Hash.NULL_HASH)) {
            transactionVM = trunk;
            trunk = transactionVM.getTrunkTransaction(tangle);
            transactionViewModels.push(transactionVM.getHash());
        }
        while(transactionViewModels.size() != 0) {
            transactionVM = TransactionViewModel.fromHash(tangle, transactionViewModels.pop());
            long currentHeight = transactionVM.getHeight();
            if(Hash.NULL_HASH.equals(trunk.getHash()) && trunk.getHeight() == 0
                    && !Hash.NULL_HASH.equals(transactionVM.getHash())) {
                if(currentHeight != 1L ){
                    transactionVM.updateHeight(1L);
                    transactionVM.update(tangle, initialSnapshot, "height");
                }
            } else if ( trunk.getType() != PREFILLED_SLOT && transactionVM.getHeight() == 0){
                long newHeight = 1L + trunk.getHeight();
                if(currentHeight != newHeight) {
                    transactionVM.updateHeight(newHeight);
                    transactionVM.update(tangle, initialSnapshot, "height");
                }
            } else {
                break;
            }
            trunk = transactionVM;
        }
    }

    public void updateSender(String sender) throws Exception {
        transaction.sender = sender;
    }
    public String getSender() {
        return transaction.sender;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TransactionViewModel other = (TransactionViewModel) o;
        return Objects.equals(getHash(), other.getHash());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHash());
    }

    //TODO: just for testing
    public void print() {
        System.out.println("Signature: " + Hex.toHexString(getSignature()));
        System.out.println("Address: " + Hex.toHexString(getAddressHash().bytes()));
        System.out.println("Value: " + transaction.value);
        System.out.println("Bundle Nonce: " + Hex.toHexString(getBundleNonceHash().bytes()));
        System.out.println("Timestamp: " + getTimestamp());
        System.out.println("Current Index: " + getCurrentIndex());
        System.out.println("Last Index: " + lastIndex());
        System.out.println("Bundle: " + Hex.toHexString(getBundleHash().bytes()));
        System.out.println("Trunk: " + Hex.toHexString(getTrunkTransactionHash().bytes()));
        System.out.println("Branch: " + Hex.toHexString(getBranchTransactionHash().bytes()));
        System.out.println("Tag: " + Hex.toHexString(getTagValue().bytes()));
        System.out.println("Attachment Timestamp: " + getAttachmentTimestamp());
        System.out.println("Attachment Timestamp Lower Bound: " + getAttachmentTimestampLowerBound());
        System.out.println("Attachment Timestamp Upper Bound: " + getAttachmentTimestampUpperBound());
        System.out.println("Nonce: " + Hex.toHexString(getNonce()));
        System.out.println("Solidity: " + isSolid());
        System.out.println("Validity: " + getValidity());
        System.out.println("Type: " + transaction.type);
        System.out.println("Snapshot: " + transaction.snapshot);
        System.out.println("Milestone: " + transaction.milestone);
        System.out.println("Height: " + transaction.height);

    }
}
