package net.helix.pendulum.event;

import java.util.Objects;

/**
 * Date: 2019-11-04
 * Author: zhelezov
 */
public class Key<T> {
    final String identifier;
    final Class<T> type;

    public Key( String identifier, Class<T> type ) {
        this.identifier = identifier;
        this.type = type;
    }

    public static <T> Key<T> key( String identifier, Class<T> type ) {
        return new Key<>( identifier, type );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Key key = (Key) o;
        return Objects.equals(identifier, key.identifier) &&
                Objects.equals(type, key.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, type);
    }
}
