package net.helix.hlx.utils.bundletypes;

import java.util.List;

public interface BundleTypes {
    void publish();
    byte[] getTxMilestone();
    byte[] getTxSibling();
    List<byte[]> getTxTips();
}
