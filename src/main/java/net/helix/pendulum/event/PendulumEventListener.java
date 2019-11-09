package net.helix.pendulum.event;

/**
 * Date: 2019-11-01
 * Author: zhelezov
 */
public interface PendulumEventListener {
    void handle(EventType type, EventContext ctx);
}
