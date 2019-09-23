package net.helix.pendulum.service.dto;

import net.helix.pendulum.service.API;

import java.util.List;

/**
 *
 * Contains information about the result of a successful {@code getTransactionStrings} API call.
 * See {@link API#getTransactionStrings} for how this response is created.
 *
 */
public class GetTransactionStringsResponse extends AbstractResponse {
	/**
	 * The raw transaction data {txs} of the specified transactions.
	 * These transaction hex strings can then be easily converted into the actual transaction object.
	 * See library functions as to how to transform back to a {@link net.helix.pendulum.model.persistables.Transaction}.
	 */
    private String[] txs;

	/**
	 * Creates a new {@link GetTransactionStringsResponse}
	 *
	 * @param elements {@link #transactionStrings}
	 * @return a {@link GetTransactionStringsResponse} filled with the provided tips
	 */
	public static GetTransactionStringsResponse create(List<String> elements) {
		GetTransactionStringsResponse res = new GetTransactionStringsResponse();
		res.txs = elements.toArray(new String[] {});
		return res;
	}

	/**
	 *
	 * @return {@link #txs}
	 */
	public String[] getTransactionStrings() {
		return txs;
	}
}
