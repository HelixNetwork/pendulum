package net.helix.hlx.utils.bundletypes;

import net.helix.hlx.service.API;
import org.bouncycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

public abstract class BundleTypes {
    byte[] senderTransaction;
    byte[] merkleTransaction;
    List<byte[]> dataTransactions = new ArrayList<>();

    public byte[] getSenderTransaction() {return senderTransaction; }
    public byte[] getMerkleTransaction() {return merkleTransaction; }
    public List<byte[]> getDataTransactions() {return dataTransactions; }
    public List<String> getTransactions() {
        List<String> transactions = new ArrayList<>();
        for (int i = dataTransactions.size()-1; i >= 0; i--) {
            transactions.add(Hex.toHexString(dataTransactions.get(i)));
        }
        transactions.add(Hex.toHexString(merkleTransaction));
        transactions.add(Hex.toHexString(senderTransaction));
        return transactions;
    }

}
