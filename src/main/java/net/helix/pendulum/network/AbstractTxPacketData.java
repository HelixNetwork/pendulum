package net.helix.pendulum.network;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;

import java.util.Arrays;

/**
 * Abstract implementation of the PacketData with the data part
 * filled with a transaction data. Good for subclassing when the first
 * part is the data of a <code>TransactionViewModel</code>
 *
 * Date: 2019-11-07
 * Author: zhelezov
 */
public abstract class AbstractTxPacketData implements PacketData {
    private TransactionViewModel txvm;

    private AbstractTxPacketData() {

    }

    public AbstractTxPacketData(TransactionViewModel txvm) {
        this.txvm = txvm;
    }

    @Override
    public byte[] getDataPart() {
        return txvm.getBytes();
    }

    @Override
    public int dataSize() {
        return TransactionViewModel.SIZE;
    }

    public final static PacketData NULL_HASH_DATA = new AbstractTxPacketData() {
        @Override
        public byte[] getDataPart() {
            byte[] data = new byte[TransactionViewModel.SIZE];
            Arrays.fill(data, (byte)0);
            return data;
        }


        @Override
        public byte[] getHashPart() {
            return Hash.NULL_HASH.bytes();
        }


    };
}
