package net.helix.pendulum.service;

import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.conf.SolidificationConfig;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TipsSolidifier {

    private static final int RESCAN_TX_TO_REQUEST_INTERVAL = 750;
    private static final int BATCH_SIZE = 10;
    private static final long LOG_DELAY = 10000l;

    private final Logger log = LoggerFactory.getLogger(TipsSolidifier.class);
    private final Tangle tangle;
    private final TipsViewModel tipsViewModel;
    private final TransactionValidator transactionValidator;
    private final SolidificationConfig config;

    private boolean shuttingDown = false;
    private Thread solidityRescanHandle;


    public TipsSolidifier(final Tangle tangle,
                          final TransactionValidator transactionValidator,
                          final TipsViewModel tipsViewModel,
                          final SolidificationConfig config) {
        this.tangle = tangle;
        this.transactionValidator = transactionValidator;
        this.tipsViewModel = tipsViewModel;
        this.config = config;
    }

    public void init() {
        if (!enabled()) {
            return;
        }

        solidityRescanHandle = new Thread(() -> {

            long lastTime = 0;
            while (!shuttingDown) {
                try {
                    boolean hasMore = true;
                    for (int i = 0; (i < BATCH_SIZE) && hasMore; i++) {
                        hasMore = scanTipsForSolidity();
                    }
                    if (log.isDebugEnabled()) {
                        long now = System.currentTimeMillis();
                        if ((now - lastTime) > LOG_DELAY) {
                            lastTime = now;
                            log.debug("#Solid/NonSolid: {}/{}", tipsViewModel.solidSize(), tipsViewModel.nonSolidSize());
                        }
                    }
                } catch (Exception e) {
                    log.error("Error during solidity scan : {}", e);
                }
                try {
                    Thread.sleep(RESCAN_TX_TO_REQUEST_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("Solidity rescan interrupted.");
                }
            }
        }, "Tip Solidity Rescan");
        solidityRescanHandle.start();
    }

    /**
     *
     * @return false if no non-solid tips left, true otherwise
     * @throws Exception if smth goes wrong with db
     */
    private boolean scanTipsForSolidity() throws Exception {
        int size = tipsViewModel.nonSolidSize();
        if (size == 0) {
            log.trace("No non-solid tips");
            return false;
        }

        Hash hash = tipsViewModel.getRandomNonSolidTipHash();

        if (hash == null) {
            log.trace("No non-solid tips");
            return false;
        }

        TransactionViewModel tipTvm = TransactionViewModel.fromHash(tangle, hash);
        if (tipTvm.getApprovers(tangle).size() != 0) {
            tipsViewModel.removeTipHash(hash);
            log.trace("{} not a tip", hash.toString());
            return true;
        }

        if (tipTvm.isSolid()) {
            tipsViewModel.setSolid(hash);
            log.trace("{} is solid already", hash.toString());
            return true;
        }

        if (transactionValidator.checkSolidity(hash)) {
            tipsViewModel.setSolid(hash);
        } else {
            log.trace("NonSolid tip txhash = {}", hash.toString());
        }
        return true;
    }

    public void shutdown() {
        if (!enabled()) {
            return;
        }

        shuttingDown = true;
        try {
            if (solidityRescanHandle != null && solidityRescanHandle.isAlive()) {
                solidityRescanHandle.join();
            }
        } catch (Exception e) {
            log.error("Error in shutdown", e);
        }

    }

    private boolean enabled() {
        return config.isTipSolidifierEnabled();
    }
}