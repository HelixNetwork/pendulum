package net.helix.pendulum.conf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZMQConfigTest {

    // We have removed the zmq_enabled param completely.
    //@Test
    public void isZmqEnabledLegacy() {
        String[] args = {
                "--zmq_enabled", "true",
        };
        PendulumConfig config = ConfigFactory.createPendulumConfig(false);
        config.parseConfigFromArgs(args);
        assertTrue("ZMQ must be globally enabled", config.isZmqEnabled());
        assertTrue("ZMQ TCP must be enabled", config.isZmqEnableTcp());
        assertTrue("ZMQ IPC must be enabled", config.isZmqEnableIpc());
    }

    @Test
    public void isZmqEnabled() {
        String[] args = {
                "--zmq_enable_tcp", "true",
                "--zmq_enable_ipc", "true",
        };
        PendulumConfig config = ConfigFactory.createPendulumConfig(false);
        config.parseConfigFromArgs(args);
        assertTrue("ZMQ must be globally enabled", config.isZmqEnabled());
        assertTrue("ZMQ TCP must be enabled", config.isZmqEnableTcp());
        assertTrue("ZMQ IPC must be enabled", config.isZmqEnableIpc());
    }

    @Test
    public void isZmqEnableTcp() {
        String[] args = {
                "--zmq_enable_tcp", "true"
        };
        PendulumConfig config = ConfigFactory.createPendulumConfig(false);
        config.parseConfigFromArgs(args);
        assertEquals("ZMQ port must be the default port", 5556, config.getZmqPort());
        assertTrue("ZMQ TCP must be enabled", config.isZmqEnableTcp());
    }

    @Test
    public void isZmqEnableIpc() {
        String[] args = {
                "--zmq_enable_ipc", "true"
        };
        PendulumConfig config = ConfigFactory.createPendulumConfig(false);
        config.parseConfigFromArgs(args);
        assertEquals("ZMQ ipc must be the default ipc", "ipc://hlx", config.getZmqIpc());
        assertTrue("ZMQ IPC must be enabled", config.isZmqEnableIpc());
    }

    @Test
    public void getZmqPort() {
        String[] args = {
                "--zmq_port", "8899"
        };
        PendulumConfig config = ConfigFactory.createPendulumConfig(false);
        config.parseConfigFromArgs(args);
        assertTrue("ZMQ TCP must be enabled", config.isZmqEnableTcp());
        assertEquals("ZMQ port must be overridden", 8899, config.getZmqPort());
    }

    @Test
    public void getZmqThreads() {
        String[] args = {
                "--zmq_threads", "5"
        };
        PendulumConfig config = ConfigFactory.createPendulumConfig(false);
        config.parseConfigFromArgs(args);
        assertEquals("ZMQ threads must be overridden", 5, config.getZmqThreads());
    }

    @Test
    public void getZmqIpc() {
        String[] args = {
                "--zmq_ipc", "ipc://test"
        };
        PendulumConfig config = ConfigFactory.createPendulumConfig(false);
        config.parseConfigFromArgs(args);
        assertTrue("ZMQ IPC must be enabled", config.isZmqEnableIpc());
        assertEquals("ZMQ ipc must be overridden", "ipc://test", config.getZmqIpc());
    }
}
