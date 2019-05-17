package net.helix.hlx.utils;

public class Pair<S, T> {
    public final S low;
    public final T hi;

    public Pair(S k, T v) {
        low = k;
        hi = v;
    }
}
