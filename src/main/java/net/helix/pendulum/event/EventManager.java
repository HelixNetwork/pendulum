package net.helix.pendulum.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This is a global event manager dispatching events to <code>PendulumEventListeners</code>
 *
 * The events are processed in a single thread, which guarantees sequential execution. Thus
 * the listeners must not block the event handling and delegate long-running tasks to a separate
 * thread if a substantial amount of work is expected. A typical pattern for a handler is to
 * extract the necessary data from <code>EventContext</code> and place it into a queue for
 * further (perhaps time consuming) processing.
 *
 *
 * Date: 2019-11-01
 * Author: zhelezov
 */
public class EventManager {
    private static final EventManager instance = new EventManager();
    private static final Logger log = LoggerFactory.getLogger(EventManager.class);

    private final ConcurrentHashMap<EventType, List<PendulumEventListener>> listeners = new ConcurrentHashMap<>();

    // guarantees sequential execution of the event handlers
    private ExecutorService executor;

    private EventManager() {
    }

    public void subscribe(EventType event, PendulumEventListener listener) {
        if (!listeners.containsKey(event)) {
            listeners.put(event, new ArrayList<>());
        }
        listeners.get(event).add(listener);
    }

    public void unsubscribe(EventType event, PendulumEventListener listener) {
        List<PendulumEventListener> users = listeners.get(event);
        if (users != null) {
            users.remove(listener);
        }
    }

    public void fire(EventType event, EventContext ctx) {
        if (executor == null) {
            log.error("EventManager is not started");
            return;
        }

        List<PendulumEventListener> users =
                Optional.ofNullable(listeners.get(event))
                        .orElse(Collections.emptyList());
        for (PendulumEventListener listener : users) {
            executor.submit(()  ->  {
                try {
                    listener.handle(event, ctx);
                } catch (Throwable t) {
                    log.warn("Error handling the event", t);
                }
            });
        }
    }

    public void start() {
        executor = Executors.newSingleThreadExecutor();
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        executor = null;
    }

    /**
     * @return The singleton instance of a global EventManager
     */
    public static EventManager get() {
        return instance;
    }
}
