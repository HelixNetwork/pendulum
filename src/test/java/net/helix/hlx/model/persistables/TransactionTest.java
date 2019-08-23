package net.helix.hlx.model.persistables;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import net.helix.hlx.TransactionTestUtils;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.Hash;


public class TransactionTest {

    @Test
    public void bytesTest() {
        Transaction t = TransactionTestUtils.getTransaction();
        
        Transaction newtx = new Transaction();
        newtx.read(t.bytes());
        newtx.readMetadata(t.metadata());
        
        assertArrayEquals("metadata should be the same in the copy", t.metadata(), newtx.metadata());
        assertArrayEquals("bytes should be the same in the copy", t.bytes(), newtx.bytes());
    }
    
    @Test
    public void fromBytesTest() {
        byte[] bytes = TransactionTestUtils.getTransactionBytes();
        
        TransactionViewModel tvm = new TransactionViewModel(bytes, Hash.NULL_HASH);
        tvm.getAddressHash();
        tvm.getTrunkTransactionHash();
        tvm.getBranchTransactionHash();
        tvm.getBundleHash();
        tvm.getTagValue();
        tvm.getBundleNonceHash(); //tvm.getObsoleteTagValue();
        tvm.setAttachmentData();
        tvm.setMetadata();
        
        assertArrayEquals("bytes in the TVM should be unmodified", tvm.getBytes(), bytes);

        Transaction tvmTransaction = tvm.getTransaction();
        
        assertEquals("branch in transaction should be the same as in the tvm", tvmTransaction.branch, tvm.getTransaction().branch);
    }

}
