package net.helix.pendulum.event;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.conf.PendulumConfig;
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
    private boolean isAsync = false;

    private EventManager() {
        try {
            isAsync = Boolean.parseBoolean(System.getProperty("eventmanager.async"));
        } catch (Exception e) {
            log.warn("Cannot parse property eventmanager.async, using default {}", isAsync);
        }
    }


    public void subscribe(EventType event, PendulumEventListener listener) {
        if (!listeners.containsKey(event)) {
            listeners.put(event, new ArrayList<>());
        }
        listeners.get(event).add(listener);
    }

    public void unsubscribe(PendulumEventListener listener) {
        for (EventType e : listeners.keySet()) {
            listeners.get(e).remove(listener);
        }
    }

    public void clear() {
        listeners.clear();
    }

    public void fire(EventType event, EventContext ctx) {
        List<PendulumEventListener> users =
                Optional.ofNullable(listeners.get(event))
                        .orElse(Collections.emptyList());

        if (isAsync) {
            doAsyncFire(users, event, ctx);
        } else {
            doFire(users, event, ctx);
        }

    }

    private void doFire(List<PendulumEventListener> users, EventType event, EventContext ctx) {
        for (PendulumEventListener listener : users) {
            listener.handle(event, ctx);
        }
    }

    private void doAsyncFire(List<PendulumEventListener> users, EventType event, EventContext ctx) {
        if (executor == null) {
            log.error("EventManager is not started");
            return;
        }

        for (PendulumEventListener listener : users) {
            listener.handle(event, ctx);
        }

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
        if (isAsync) {
            executor = Executors.newSingleThreadExecutor();
        }
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
