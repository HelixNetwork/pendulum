package net.helix.pendulum.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Date: 2019-11-01
 * Author: zhelezov
 */
public class EventManager {
    private static final EventManager instance = new EventManager();
    private static final Logger log = LoggerFactory.getLogger(EventManager.class);

    private final ConcurrentHashMap<EventType, List<PendulumEventListener>> listeners = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
        List<PendulumEventListener> users =
                Optional.ofNullable(listeners.get(event))
                        .orElse(Collections.emptyList());
        executor.submit(() -> {
            for (PendulumEventListener listener : users) {
                try {
                    listener.handle(event, ctx);
                } catch (Throwable t) {
                    log.error("Error handling the event", t);
                }
            }
        });
    }

    /**
     * @return The singleton instance of a global EventManager
     */
    public static EventManager get() {
        return instance;
    }
}
