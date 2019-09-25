package net.helix.pendulum.network;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.conf.NodeConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.storage.Tangle;
import org.junit.*;
import org.mockito.*;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.*;


public class NodeTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private NodeConfig nodeConfig;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Captor
    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;

    private Node classUnderTest;
    private static final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @Before
    public void setUp() {
        // inject our mock appender
        logger.addAppender(mockAppender);

        // set up class under test
        nodeConfig = mock(NodeConfig.class);
        classUnderTest = new Node(null, null, null, null, null, null, nodeConfig);

        // verify config calls in Node constructor
        verify(nodeConfig).getRequestHashSize();
        verify(nodeConfig).getTransactionPacketSize();
    }

    @After
    public void shutdown() {
        logger.detachAppender(mockAppender);
    }

    @Test
    public void spawnNeighborDNSRefresherThreadTest() {
        when(nodeConfig.isDnsResolutionEnabled()).thenReturn(false);
        Runnable runnable = classUnderTest.spawnNeighborDNSRefresherThread();
        runnable.run();
        verify(nodeConfig).isDnsResolutionEnabled();
        verifyNoMoreInteractions(nodeConfig);

        // verify logging
        verify(mockAppender).doAppend(captorLoggingEvent.capture());
        final ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        Assert.assertEquals("Loglevel must be info", Level.INFO, loggingEvent.getLevel());
        Assert.assertEquals("Invalid log message", "Ignoring DNS Refresher Thread... DNS_RESOLUTION_ENABLED is false", loggingEvent.getFormattedMessage());

    }

    @Test
    public void whenProcessReceivedDataSetArrivalTimeToCurrentMillis() throws Exception {
        Node node = new Node(mock(Tangle.class), mock(SnapshotProvider.class), mock(TransactionValidator.class), null, null, null, mock(NodeConfig.class));
        TransactionViewModel transaction = mock(TransactionViewModel.class);
        // It is important to stub the getHash method here because processReceivedData will broadcast the transaction.
        // This might sometimes (concurrency issue) lead to a NPE in the process receiver thread.
        // See executor.submit(spawnProcessReceivedThread()) -> Node.weightQueue -> transaction.getHash().bytes()[i]
        when(transaction.getHash()).thenReturn(Hash.NULL_HASH);
        when(transaction.store(any(), any())).thenReturn(true);
        Neighbor neighbor = mock(Neighbor.class, Answers.RETURNS_SMART_NULLS.get());
        node.processReceivedData(transaction, neighbor);
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
