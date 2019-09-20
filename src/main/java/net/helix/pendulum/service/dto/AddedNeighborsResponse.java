package net.helix.pendulum.service.dto;

public class AddedNeighborsResponse extends AbstractResponse {

	/**
	 * The amount of temporally added neighbors to this node.
	 * Can be 0 or more.
	 */
	private int addedNeighbors;

	/**
	 * Creates a new {@link AddedNeighborsResponse}
	 *
	 * @param numberOfAddedNeighbors {@link #addedNeighbors}
	 * @return an {@link AddedNeighborsResponse} filled with the number of added neighbors
	 */
	public static AbstractResponse create(int numberOfAddedNeighbors) {
		AddedNeighborsResponse res = new AddedNeighborsResponse();
		res.addedNeighbors = numberOfAddedNeighbors;
		return res;
	}

	/**
	 *
	 * @return {link #addedNeighbors}
	 */
	public int getAddedNeighbors() {
		return addedNeighbors;
	}
	
}
