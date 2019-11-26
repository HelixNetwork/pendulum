package net.helix.pendulum.event;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;

/**
 * Date: 2019-11-11
 * Author: zhelezov
 */
public class EventUtils {
    public static Hash getTxHash(EventContext ec) {
        return ec.get(Key.key("TX_HASH", Hash.class));
    }

    public static EventContext fromTxHash(Hash txHash) {
        EventContext ctx = new EventContext();
        ctx.put(Key.key("TX_HASH", Hash.class), txHash);
        return ctx;
    }
}
