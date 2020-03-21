package net.helix.pendulum.network;

import net.helix.pendulum.Pendulum;

import java.net.DatagramPacket;

/**
 * Date: 2019-11-07
 * Author: zhelezov
 */
public interface DatagramFactory extends Pendulum.Initializable {
    DatagramPacket create(PacketData dataProvider);
}
