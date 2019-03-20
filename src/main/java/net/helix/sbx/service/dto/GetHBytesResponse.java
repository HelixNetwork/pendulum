package net.helix.sbx.service.dto;

import java.util.List;

import net.helix.sbx.service.API;

/**
 *
 * Contains information about the result of a successful {@code getBytes} API call.
 * See {@link API#getHBytesStatement} for how this response is created.
 *
 */
public class GetHBytesResponse extends AbstractResponse {
	/**
	 * The raw transaction data {hbytes} of the specified transactions.
	 * These hbytes can then be easily converted into the actual transaction object.
	 * See library functions as to how to transform back to a {@link net.helix.sbx.model.persistables.Transaction}.
	 */
    private String[] hbytes;

	/**
	 * Creates a new {@link GetHBytesResponse}
	 *
	 * @param elements {@link #hbytes}
	 * @return a {@link GetHBytesResponse} filled with the provided tips
	 */
	public static GetHBytesResponse create(List<String> elements) {
		GetHBytesResponse res = new GetHBytesResponse();
		res.hbytes = elements.toArray(new String[] {});
		return res;
	}

	/**
	 *
	 * @return {@link #hbytes}
	 */
	public String [] getHBytes() {
		return hbytes;
	}
}
