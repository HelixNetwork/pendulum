package net.helix.hlx.service.dto;

import net.helix.hlx.service.API;

import java.util.List;

/**
 *
 * Contains information about the result of a successful {@code getBytes} API call.
 * See {@link API#getTxHexStatement} for how this response is created.
 *
 */
public class GetTransactionStringsResponse extends AbstractResponse {
	/**
	 * The raw transaction data {txHex} of the specified transactions.
	 * These transaction hex strings can then be easily converted into the actual transaction object.
	 * See library functions as to how to transform back to a {@link net.helix.hlx.model.persistables.Transaction}.
	 */
    private String[] transactionStrings;

	/**
	 * Creates a new {@link GetTransactionStringsResponse}
	 *
	 * @param elements {@link #txHex}
	 * @return a {@link GetTransactionStringsResponse} filled with the provided tips
	 */
	public static GetTransactionStringsResponse create(List<String> elements) {
		GetTransactionStringsResponse res = new GetTransactionStringsResponse();
		res.transactionStrings = elements.toArray(new String[] {});
		return res;
	}

	/**
	 *
	 * @return {@link #txHex}
	 */
	public String [] getTransactionStrings() {
		return transactionStrings;
	}
}
