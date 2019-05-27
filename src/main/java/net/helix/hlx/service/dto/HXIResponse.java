package net.helix.hlx.service.dto;

import net.helix.hlx.HXI;

/**
 * <p>
 *     When a command is not recognized by the default API, we try to process it as a HXI module.
 *     HXI stands for Helix eXtension Interface. See {@link HXI} for more information.
 * </p>
 * <p>
 *     The response will contain the reply that the HXI module gave.
 *     This could be empty, depending on the module.
 * </p>
 *
 *
 */
public class HXIResponse extends AbstractResponse {
    private Object HXI;

    public static HXIResponse create(Object myHXI) {
        HXIResponse HXIResponse = new HXIResponse();
        HXIResponse.HXI = myHXI;
        return HXIResponse;
    }

    public Object getResponse() {
        return HXI;
    }
}
