package net.helix.sbx.service;

import java.util.Map;

public interface CallableRequest<V> {
    V call(Map<String, Object> request);
}
