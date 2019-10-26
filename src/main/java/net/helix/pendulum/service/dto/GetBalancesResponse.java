package net.helix.pendulum.service.dto;

import net.helix.pendulum.service.API;

import java.util.List;

/**
 *
 * Contains information about the result of a successful {@code getBalances} API call.
 * See {@link API#getBalancesStatement} for how this response is created.
 *
 */
public class GetBalancesResponse extends AbstractResponse {

	/**
	 * The balances as a list in the same order as the addresses were provided as input
	 */
	private List<String> balances;

	/**
	 * The milestone index with which the confirmed balance was determined
	 */
	private int milestoneIndex;

	/**
	 * Creates a new {@link GetBalancesResponse}
	 *
	 * @param elements {@link #balances}
	 * @param milestoneIndex {@link #milestoneIndex}
	 * @return an {@link GetBalancesResponse} filled with the balances, references used and index used
	 */
	public static AbstractResponse create(List<String> elements,  int milestoneIndex) {
		GetBalancesResponse res = new GetBalancesResponse();
		res.balances = elements;
		res.milestoneIndex = milestoneIndex;
		return res;
	}

	/**
	 *
	 * @return {@link #milestoneIndex}
	 */
	public int getMilestoneIndex() {
		return milestoneIndex;
	}

	/**
	 *
	 * @return {@link #balances}
	 */
	public List<String> getBalances() {
		return balances;
	}
}
