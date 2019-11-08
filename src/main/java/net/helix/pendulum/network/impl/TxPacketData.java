package net.helix.pendulum.network.impl;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.network.PacketData;

import java.util.Arrays;

/**
 * Abstract implementation of the PacketData with the data part
 * filled with a transaction data. Good for subclassing when the first
 * part is the data of a <code>TransactionViewModel</code>
 *
 * Date: 2019-11-07
 * Author: zhelezov
 */
public class TxPacketData implements PacketData {
    private TransactionViewModel txvm;
    private Hash hash;

    private TxPacketData() {

    }

    public TxPacketData(TransactionViewModel txvm, Hash hash) {
        this.hash = hash;
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

    @Override
    public byte[] getHashPart() {
        return hash.bytes();
    }

    public final static PacketData NULL_HASH_DATA = new TxPacketData() {
        private byte[] zeroBytes;
        private byte[] nullHashBytes;

        {
            //filling null hashes
            zeroBytes = new byte[TransactionViewModel.SIZE];
            Arrays.fill(zeroBytes, (byte)0);
            nullHashBytes = Hash.NULL_HASH.bytes();
        }

        @Override
        public byte[] getDataPart() {
            return zeroBytes;
        }


        @Override
        public byte[] getHashPart() {
            return nullHashBytes;
        }


    };
}
