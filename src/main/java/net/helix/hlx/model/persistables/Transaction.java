package net.helix.hlx.model.persistables;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.storage.Persistable;
import net.helix.hlx.utils.Serializer;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;

import static net.helix.hlx.controllers.TransactionViewModel.TAG_SIZE;

/**
 * Created by paul on 3/2/17 for hlx.
 */

 /**
 * The Transaction model class is an implementation of the <code> Persistable </code> interface.
 * It contains the byte array of the transaction hash and a lot of meta data like the hashes of address and bundle,
 * the value, the timestamp or the tag hash.
 * To get and read transaction bytes @see #bytes() and @see #read(byte[]).
 * To get and read meta data bytes @see #metadata() and @see #readMetadata(byte[]).
 */
public class Transaction implements Persistable {
    public static final int SIZE = 768;

     /**
      * Bitmask used to access and store the solid flag.
      */
     public static final int IS_SOLID_BITMASK = 0b01;

     /**
      * Bitmask used to access and store the milestone flag.
      */
     public static final int IS_MILESTONE_BITMASK = 0b10;

    public byte[] bytes;

    public Hash address;
    public Hash bundle;
    public Hash trunk;
    public Hash branch;
    public Hash bundleNonce; // formerly obsoleteTag
    public long value;
    public long currentIndex;
    public long lastIndex;
    public long timestamp;

    public Hash tag;
    public long attachmentTimestamp;
    public long attachmentTimestampLowerBound;
    public long attachmentTimestampUpperBound;

    public int validity = 0;
    public int type = TransactionViewModel.PREFILLED_SLOT;
    public long arrivalTime = 0;

    //public boolean confirmed = false;
    public boolean parsed = false;
    public boolean solid = false;

     /**
      * This flag indicates if the transaction is a coordinator issued milestone.
      */
     public boolean milestone = false;

    public long height = 0;
    public String sender = "";
    public int snapshot;

    public byte[] bytes() {
        return bytes;
    }

    public void read(byte[] bytes) {
        if(bytes != null) {
            this.bytes = new byte[SIZE];
            System.arraycopy(bytes, 0, this.bytes, 0, SIZE);
            this.type = TransactionViewModel.FILLED_SLOT;
        }
    }

    @Override
    public byte[] metadata() {
        int allocateSize =
                Hash.SIZE_IN_BYTES * 5 + //address,bundle,trunk,branch,bundleNonce 160
                        TAG_SIZE + //tag 8
                        Long.BYTES * 9 + //value,currentIndex,lastIndex,timestamp,attachmentTimestampLowerBound,attachmentTimestampUpperBound,arrivalTime,height 72
                        Integer.BYTES * 3 + //validity,type,snapshot 12
                        1 + //solid
                        sender.getBytes().length; //sender
        ByteBuffer buffer = ByteBuffer.allocate(allocateSize);
        buffer.put(address.bytes());
        buffer.put(bundle.bytes());
        buffer.put(trunk.bytes());
        buffer.put(branch.bytes());
        buffer.put(bundleNonce.bytes());
        buffer.put(Serializer.serialize(value));
        buffer.put(Serializer.serialize(currentIndex));
        buffer.put(Serializer.serialize(lastIndex));
        buffer.put(Serializer.serialize(timestamp));

        buffer.put(tag.bytes());
        buffer.put(Serializer.serialize(attachmentTimestamp));
        buffer.put(Serializer.serialize(attachmentTimestampLowerBound));
        buffer.put(Serializer.serialize(attachmentTimestampUpperBound));

        buffer.put(Serializer.serialize(validity));
        buffer.put(Serializer.serialize(type));
        buffer.put(Serializer.serialize(arrivalTime));
        buffer.put(Serializer.serialize(height));
        //buffer.put((byte) (confirmed ? 1:0));

        byte flags = 0;
        flags |= solid ? IS_SOLID_BITMASK : 0;
        flags |= milestone ? IS_MILESTONE_BITMASK : 0;
        buffer.put(flags);

        buffer.put(Serializer.serialize(snapshot));
        buffer.put(sender.getBytes());
        return buffer.array();
    }

    @Override
    public void readMetadata(byte[] bytes) {
        int i = 0;
        if(bytes != null) {
            address = HashFactory.ADDRESS.create(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            bundle = HashFactory.BUNDLE.create(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            trunk = HashFactory.TRANSACTION.create(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            branch = HashFactory.TRANSACTION.create(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            bundleNonce = HashFactory.BUNDLENONCE.create(bytes, i, Hash.SIZE_IN_BYTES);
            i += Hash.SIZE_IN_BYTES;
            value = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            currentIndex = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            lastIndex = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            timestamp = Serializer.getLong(bytes, i);
            i += Long.BYTES;

            tag = HashFactory.TAG.create(bytes, i, TAG_SIZE);
            i += TAG_SIZE;
            attachmentTimestamp = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            attachmentTimestampLowerBound = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            attachmentTimestampUpperBound = Serializer.getLong(bytes, i);
            i += Long.BYTES;

            validity = Serializer.getInteger(bytes, i);
            i += Integer.BYTES;
            type = Serializer.getInteger(bytes, i);
            i += Integer.BYTES;
            arrivalTime = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            height = Serializer.getLong(bytes, i);
            i += Long.BYTES;
            /*
            confirmed = bytes[i] == 1;
            i++;
            */

            // decode the boolean byte by checking the bitmasks
            solid = (bytes[i] & IS_SOLID_BITMASK) != 0;
            milestone = (bytes[i] & IS_MILESTONE_BITMASK) != 0;
            i++;

            snapshot = Serializer.getInteger(bytes, i);
            i += Integer.BYTES;
            byte[] senderBytes = new byte[bytes.length - i];
            if (senderBytes.length != 0) {
                System.arraycopy(bytes, i, senderBytes, 0, senderBytes.length);
            }
            sender = new String(senderBytes);
            parsed = true;
        }
    }

    @Override
    public boolean merge() {
        return false;
    }

     @Override
     public String toString() {
         return "Transaction{" +
                 " address=" + address +
                 ", bundle=" + bundle +
                 ", trunk=" + trunk +
                 ", branch=" + branch +
                 ", bundleNonce=" + bundleNonce +
                 ", value=" + value +
                 ", currentIndex=" + currentIndex +
                 ", lastIndex=" + lastIndex +
                 ", timestamp=" + timestamp +
                 ", tag=" + tag +
                 ", attachmentTimestamp=" + attachmentTimestamp +
                 ", attachmentTimestampLowerBound=" + attachmentTimestampLowerBound +
                 ", attachmentTimestampUpperBound=" + attachmentTimestampUpperBound +
                 ", validity=" + validity +
                 ", type=" + type +
                 ", arrivalTime=" + arrivalTime +
                 ", parsed=" + parsed +
                 ", solid=" + solid +
                 ", milestone=" + milestone +
                 ", height=" + height +
                 ", sender='" + sender + '\'' +
                 ", snapshot=" + snapshot +
                 ", bytes=" + Hex.toHexString(bytes) +
                 '}';
     }
}
