package net.helix.hlx.service;

import java.util.Map;

public interface CallableRequest<V> {
    V call(Map<String, Object> request);
}
