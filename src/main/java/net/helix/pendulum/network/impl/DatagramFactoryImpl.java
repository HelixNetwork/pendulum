package net.helix.pendulum.network.impl;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.network.DatagramFactory;
import net.helix.pendulum.network.PacketData;

import java.net.DatagramPacket;

/**
 * Date: 2019-11-07
 * Author: zhelezov
 */
public class DatagramFactoryImpl implements DatagramFactory {

    private int requestHashSize;
    private int packetSize;
    //private DatagramPacket sendingPacket;
    //private DatagramPacket tipRequestingPacket;

    @Override
    public Pendulum.Initializable init() {
        PendulumConfig config = Pendulum.ServiceRegistry.get().resolve(PendulumConfig.class);

        this.requestHashSize = config.getRequestHashSize();
        this.packetSize = config.getTransactionPacketSize();

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
