package net.helix.pendulum.event;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Date: 2019-11-01
 * Author: zhelezov
 */
public class EventManager {
    private static final EventManager instance = new EventManager();

    private static final ConcurrentHashMap<EventType, List<PendulumEventListener>> listeners = new ConcurrentHashMap<>();

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
        for (PendulumEventListener listener : users) {
            listener.handle(event, ctx);
        }
    }

    /**
     * @return The singleton instance of a global EventManager
     */
    public static EventManager get() {
        return instance;
    }
}
