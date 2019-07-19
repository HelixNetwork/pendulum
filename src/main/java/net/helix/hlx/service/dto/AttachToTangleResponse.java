package net.helix.hlx.service.dto;

import net.helix.hlx.service.API;

import java.util.List;

/**
 *
 * Contains information about the result of a successful {@code attachToTangle} API call.
 * @see {@link API#attachToTangleStatement} for how this response is created.
 *
 */
public class AttachToTangleResponse extends AbstractResponse {

	/**
	 * List of the attached transactions in hexadecimal representation.
	 * The last 96 bytes of the return value consist of the:
	 * <code>trunkTransaction</code> + <code>branchTransaction</code> + <code>nonce</code>.
	 */
	private List<String> transactionStrings;

	/**
	 * Creates a new {@link AttachToTangleResponse}
	 * @param elements {@link #transactionStrings}
	 * @return an {@link AttachToTangleResponse} filled with the txs
	 */
	public static AbstractResponse create(List<String> elements) {
		AttachToTangleResponse res = new AttachToTangleResponse();
		res.transactionStrings = elements;
		return res;
	}

	/**
	 *
	 * @return {@link #transactionStrings}
	 */
	public List<String> getTransactionStrings() {
		return transactionStrings;
	}
}
