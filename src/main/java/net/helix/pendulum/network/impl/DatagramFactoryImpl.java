package net.helix.pendulum.network.impl;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.network.DatagramFactory;
import net.helix.pendulum.network.PacketData;
import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;

/**
 * Date: 2019-11-07
 * Author: zhelezov
 */
public class DatagramFactoryImpl implements DatagramFactory {
    private static final Logger log = LoggerFactory.getLogger(DatagramFactory.class);

    private int requestHashSize;
    private int packetSize;
    //private DatagramPacket sendingPacket;
    //private DatagramPacket tipRequestingPacket;

    @Override
    public Pendulum.Initializable init() {
        PendulumConfig config = Pendulum.ServiceRegistry.get().resolve(PendulumConfig.class);

        this.requestHashSize = config.getRequestHashSize() > 0 ?
                config.getRequestHashSize() : Hash.SIZE_IN_BYTES;
        this.packetSize = config.getTransactionPacketSize() > 0
                ? config.getTransactionPacketSize() : TransactionViewModel.SIZE + Hash.SIZE_IN_BYTES;

        log.debug("Packet size, hash size: {} {}", requestHashSize, packetSize);
        return this;
    }

    @Override
    public DatagramPacket create(PacketData dataProvider) {
        byte[] buffer = new byte[packetSize];
        int dataOffset = dataProvider.dataSize();

        System.arraycopy(dataProvider.getDataPart(), 0, buffer, 0, dataOffset);
        System.arraycopy(dataProvider.getHashPart(), 0, buffer, dataOffset, requestHashSize);

        return new DatagramPacket(buffer, packetSize);
    }
}
