package net.helix.pendulum.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Date: 2019-11-01
 * Author: zhelezov
 */
public class EventManager {
    private static final EventManager instance = new EventManager();

    private static final Map<EventType, List<PendulumEventListener>> listeners = new HashMap<>();

    private EventManager() {
    }

    public <T> void subscribe(EventType event, PendulumEventListener listener) {
        if (!listeners.containsKey(event)) {
            listeners.put(event, new ArrayList<>());
        }
        listeners.get(event).add(listener);
    }

    public <T> void unsubscribe(EventType event, PendulumEventListener listener) {
        List<PendulumEventListener> users = listeners.get(event);
        if (users != null)
            users.remove(listener);
    }

    public <T> void fire(EventType event, EventContext ctx) {
        List<PendulumEventListener> users = listeners.get(event);
        for (PendulumEventListener listener : users) {
            listener.handle(event, ctx);
        }
    }

    /**
     * @return The singleton instance of the system-vide EventManager
     */
    public static EventManager get() {
        return instance;
    }
}
