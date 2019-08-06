package net.helix.hlx.utils.bundletypes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NomineeBundle implements BundleTypes {

    private static final Logger log = LoggerFactory.getLogger(NomineeBundle.class);
    public NomineeBundle() {
    }
    @Override
    public void publish() {
    }

    @Override
    public byte[] getTxMilestone() {
        return new byte[0];
    }

    @Override
    public byte[] getTxSibling() {
        return new byte[0];
    }

    @Override
    public List<byte[]> getTxTips() {
        return null;
    }
}
