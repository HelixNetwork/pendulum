package net.helix.pendulum.network;

import net.helix.pendulum.conf.NodeConfig;
import net.helix.pendulum.event.EventContext;
import net.helix.pendulum.event.EventManager;
import net.helix.pendulum.event.EventType;
import net.helix.pendulum.event.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by paul on 4/16/17.
 */
public class UDPReceiver {
    private static final Logger log = LoggerFactory.getLogger(UDPReceiver.class);

    private final DatagramPacket receivingPacket;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final int port;
    private final Node node;
    private final int packetSize;

    private DatagramSocket socket;


    private Thread receivingThread;

    public UDPReceiver(Node node, NodeConfig config) {
        this.node = node;
        this.port = config.getUdpReceiverPort();
        this.packetSize = config.getTransactionPacketSize();
        this.receivingPacket = new DatagramPacket(new byte[packetSize], packetSize);
    }

    public void init() throws Exception {

        socket = new DatagramSocket(port);
        node.setUDPSocket(socket);
        log.info("UDP replicator is accepting connections on udp port " + port);

        receivingThread = new Thread(spawnReceiverThread(), "UDP receiving thread");
        receivingThread.start();
    }

    private Runnable spawnReceiverThread() {
        return () -> {


            log.info("Spawning Receiver Thread");

            int processed = 0;
            int dropped = 0;

            while (!shuttingDown.get()) {

                if (((processed + dropped) % 50000 == 49999)) {
                    log.info("Receiver thread processed/dropped ratio: "+processed+"/"+dropped);
                    processed = 0;
                    dropped = 0;
                }

                try {
                    socket.receive(receivingPacket);

                    if (receivingPacket.getLength() == packetSize) {

                        byte[] bytes = Arrays.copyOf(receivingPacket.getData(), receivingPacket.getLength());
                        SocketAddress address = receivingPacket.getSocketAddress();

                        EventContext ctx = new EventContext();
                        ctx.put(Key.key("BYTES", byte[].class), bytes);
                        ctx.put(Key.key("SENDER", SocketAddress.class), address);
                        ctx.put(Key.key("URI_SCHEME", String.class), "udp");

                        EventManager.get().fire(EventType.NEW_BYTES, ctx);
                        processed++;

                        Thread.yield();

                    } else {
                        receivingPacket.setLength(packetSize);
                    }
                } catch (final RejectedExecutionException e) {
                    //no free thread, packet dropped
                    dropped++;

                } catch (final Exception e) {
                    log.error("Receiver Thread Exception:", e);
                }
            }
            log.info("Shutting down spawning Receiver Thread");
        };
    }

    public void send(final DatagramPacket packet) {
        try {
            if (socket != null) {
                socket.send(packet);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        try {
            receivingThread.join(6000L);
        }
        catch (Exception e) {
            // ignore
        }
    }

}