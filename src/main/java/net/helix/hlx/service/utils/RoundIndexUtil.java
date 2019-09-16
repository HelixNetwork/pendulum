package net.helix.hlx.service.utils;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.utils.Serializer;

public class RoundIndexUtil {

    public static long getCurrentTime() {
        return System.currentTimeMillis();
    }

    public static int getRoundIndex(TransactionViewModel milestoneTransaction) {
        return (int) Serializer.getLong(milestoneTransaction.getBytes(), TransactionViewModel.TAG_OFFSET);
    }

    public static int getRound(long time, long genesisTime, long roundDuration) {
        return getRound(time, genesisTime, roundDuration, 0);
    }

    public static int getRound(long time, long genesisTime, long roundDuration, long offset) {
        return (int) ((time - genesisTime) / roundDuration + offset) & 0x000000001fffff;
    }

    public static boolean isRoundActive(long time, long genesisTime, long roundDuration, long roundPause) {
        return (time - genesisTime) % roundDuration < roundDuration - roundPause;
    }

    public static long getStartTime(long genesis, long roundDuration, int round) {
        return genesis + (round * roundDuration);
    }
}
