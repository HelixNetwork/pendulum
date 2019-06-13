package net.helix.hlx.network;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assert;

import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import net.helix.hlx.model.Hash;
import net.helix.hlx.conf.NodeConfig;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.TransactionValidator;


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
    public void setup() {
        // inject our mock appender
        logger.addAppender(mockAppender);

        // set up class under test
        nodeConfig = mock(NodeConfig.class);
        classUnderTest = new Node(null, null, null, null, null, null, nodeConfig, null);

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
        Node node = new Node(mock(Tangle.class), mock(SnapshotProvider.class), mock(TransactionValidator.class), null, null, null, mock(NodeConfig.class), null);
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
