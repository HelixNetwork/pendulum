package net.helix.pendulum.service;

import java.util.Map;

public interface CallableRequest<V> {
    V call(Map<String, Object> request);
}
