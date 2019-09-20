package net.helix.pendulum.service.restserver;

import net.helix.pendulum.service.dto.AbstractResponse;

import java.net.InetAddress;

/**
 *
 * Interface that defines the API call handling
 *
 */
@FunctionalInterface
public interface ApiProcessor {

    /**
     * Processes the request according to the
     *
     * @param request the request body, unprocessed
     * @param inetAddress the address from the API caller
     * @return The response for this request
     */
    AbstractResponse processFunction(String request, InetAddress inetAddress);
}
