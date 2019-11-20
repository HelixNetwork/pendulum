package net.helix.pendulum.network;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import net.helix.pendulum.AbstractPendulumTest;
import net.helix.pendulum.Pendulum;
import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.conf.NodeConfig;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.storage.Tangle;
import org.bouncycastle.jce.provider.JDKKeyFactory;
import org.junit.*;
import org.mockito.*;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.verification.VerificationMode;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;

import static org.mockito.Mockito.*;


public class NodeTest extends AbstractPendulumTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private PendulumConfig nodeConfig;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Captor
    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;

    private Node classUnderTest;
    private static final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @Before
    public void setUp() throws Exception {
        // inject our mock appender
        super.setUp();
        logger.addAppender(mockAppender);

        // set up class under test
        nodeConfig = mock(PendulumConfig.class);
        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, nodeConfig);

        classUnderTest = new Node();
        classUnderTest.init();

        // verify config calls in Node constructor
        verify(nodeConfig, atLeast(1)).getRequestHashSize();
        verify(nodeConfig, atLeast(1)).getTransactionPacketSize();
    }

    @After
    public void shutdown() throws Exception {
        Pendulum.ServiceRegistry.get().register(PendulumConfig.class, config);
        logger.detachAppender(mockAppender);
    }

    @Test
    public void spawnNeighborDNSRefresherThreadTest() {
        when(nodeConfig.isDnsRefresherEnabled()).thenReturn(false);
        Node n = spy(classUnderTest);
        Neighbor neighbor = mock(Neighbor.class);
        n.addNeighbor(neighbor);

        doReturn(new InetSocketAddress(123)).when(neighbor).getAddress();
        doReturn(java.util.Optional.of("127.0.0.1")).when(n).checkIp(anyString());
        doReturn(false).when(n).match(anyString(), anyString());
        doCallRealMethod().when(n).checkAllDns();

        n.checkAllDns();

        verify(nodeConfig).isDnsRefresherEnabled();

        // verify logging
        verify(mockAppender, atLeast(1)).doAppend(captorLoggingEvent.capture());
        final ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        Assert.assertEquals("Loglevel must be info", Level.INFO, loggingEvent.getLevel());
        Assert.assertTrue("Invalid log message", loggingEvent.getFormattedMessage().contains("Skipping... DNS_REFRESHER_ENABLED is false."));

    }

    @Test
    public void whenProcessReceivedDataSetArrivalTimeToCurrentMillis() throws Exception {
        Node node = new Node(mock(Tangle.class), mock(SnapshotProvider.class), mock(TransactionValidator.class), null, null, mock(NodeConfig.class));
        TransactionViewModel transaction = mock(TransactionViewModel.class);
        // It is important to stub the getHash method here because processReceivedData will broadcast the transaction.
        // This might sometimes (concurrency issue) lead to a NPE in the process receiver thread.
        // See executor.submit(spawnProcessReceivedThread()) -> Node.weightQueue -> transaction.getHash().bytes()[i]
        when(transaction.getHash()).thenReturn(Hash.NULL_HASH);
        when(transaction.store(any(), any())).thenReturn(true);
        Neighbor neighbor = mock(Neighbor.class, Answers.RETURNS_SMART_NULLS.get());
        node.processReceivedTx(transaction, neighbor);
        verify(transaction).setArrivalTime(longThat(
                new ArgumentMatcher() {
                    @Override
                    public boolean matches(Object arrival) {
                        long now = System.currentTimeMillis() / 1000;
                        return (Long)arrival > now - 1000 && (Long)arrival <= now;
                    }
                }
        ));
    }

}
