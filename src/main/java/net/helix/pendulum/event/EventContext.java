package net.helix.pendulum.event;

import java.util.HashMap;
import java.util.Map;

/**
 * Date: 2019-11-04
 * Author: zhelezov
 */
public class EventContext {
    private final Map<Key<?>, Object> values = new HashMap<>();

    public <T> void put( Key<T> key, T value ) {
        values.put( key, value );
    }

    public <T> T get( Key<T> key ) {
        return key.type.cast( values.get( key ) );
    }
}
