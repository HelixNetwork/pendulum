package net.helix.pendulum.event;

import net.helix.pendulum.controllers.TransactionViewModel;

/**
 * Date: 2019-11-11
 * Author: zhelezov
 */
public class EventUtils {
    public static TransactionViewModel getTx(EventContext ec) {
        return ec.get(Key.key("TX", TransactionViewModel.class));
    }

    public static EventContext fromTx(TransactionViewModel txvm) {
        EventContext ctx = new EventContext();
        ctx.put(Key.key("TX", TransactionViewModel.class), txvm);
        return ctx;
    }
}