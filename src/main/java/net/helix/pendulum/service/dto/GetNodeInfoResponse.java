package net.helix.pendulum.service.dto;

import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.API;
import net.helix.pendulum.service.Feature;
import org.bouncycastle.util.encoders.Hex;

/**
 *
 * Contains information about the result of a successful {@code getNodeInfo} API call.
 * See {@link API#getNodeInfoStatement} for how this response is created.
 *
 */
public class GetNodeInfoResponse extends AbstractResponse {

	/**
	 * Name of the Helix software you're currently using. (SBX stands for Sandbox)
	 */
	private String appName;

	/**
	 * The version of the Helix software this node is running.
	 */
	private String appVersion;

	/**
	 * Available cores for JRE on this node.
	 */
	private int jreAvailableProcessors;

	/**
	 * The amount of free memory in the Java Virtual Machine.
	 */
	private long jreFreeMemory;

	/**
	 * The JRE version this node runs on
	 */
	private String jreVersion;

	/**
	 * The maximum amount of memory that the Java virtual machine will attempt to use.
	 */
    private long jreMaxMemory;

	/**
	 * The total amount of memory in the Java virtual machine.
	 */
    private long jreTotalMemory;


	/**
	 * Index of the current round
	 */
    private int currentRoundIndex;

	/**
	 * The merkle root of the votes contained in the latest solid round.
	 */
    private String latestSolidRoundHash;

	/**
	 * Index of the {@link #latestSolidRoundHash}
	 */
    private int latestSolidRoundIndex;

	/**
	 * The start index of the milestones.
	 * This index is encoded in each milestone transaction by the coordinator
	 */
    private int roundStartIndex;

	/**
	 * The index of the round used in the latest snapshot.
	 * This is the most recent round in the entire snapshot
	 */
	private int lastSnapshottedRoundIndex;

	/**
	 * Number of neighbors this node is directly connected with.
	 */
	private int neighbors;

	/**
	 * The amount of transaction packets which are currently waiting to be broadcast.
	 */
	private int packetsQueueSize;

	/**
	 * The difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC
	 */
	private long time;

	/**
	 * Number of tips in the network.
	 */
	private int tips;

	/**
	 * When a node receives a transaction from one of its neighbors,
	 * this transaction is referencing two other transactions t1 and t2 (trunk and branch transaction).
	 * If either t1 or t2 (or both) is not in the node's local database,
	 * then the transaction hash of t1 (or t2 or both) is added to the queue of the "transactions to request".
	 * At some point, the node will process this queue and ask for details about transactions in the
	 *  "transaction to request" queue from one of its neighbors.
	 * This number represents the amount of "transaction to request"
	 */
	private int transactionsToRequest;

	/**
	 * Every node can have features enabled or disabled.
	 * This list will contain all the names of the features of a node as specified in {@link Feature}.
	 */
	private String[] features;

	/**
	 * Creates a new {@link GetNodeInfoResponse}
	 *
	 * @param appName {@link #appName}
	 * @param appVersion {@link #appVersion}
	 * @param jreAvailableProcessors {@link #jreAvailableProcessors}
	 * @param jreFreeMemory {@link #jreFreeMemory}
	 * @param jreVersion {@link #jreVersion}
	 * @param maxMemory {@link #jreMaxMemory}
	 * @param totalMemory {@link #jreTotalMemory}
	 * @param currentRoundIndex {@link #currentRoundIndex}
	 * @param latestSolidRoundHash {@link #latestSolidRoundHash}
	 * @param latestSolidRoundIndex {@link #latestSolidRoundIndex}
	 * @param roundStartIndex {@link #roundStartIndex}
	 * @param lastSnapshottedRoundIndex {@link #lastSnapshottedRoundIndex}
	 * @param neighbors {@link #neighbors}
	 * @param packetsQueueSize {@link #packetsQueueSize}
	 * @param currentTimeMillis {@link #time}
	 * @param tips {@link #tips}
	 * @param numberOfTransactionsToRequest {@link #transactionsToRequest}
	 * @param features {@link #features}
	 * @return a {@link GetNodeInfoResponse} filled with all the provided parameters
	 */
	public static AbstractResponse create(String appName, String appVersion, int jreAvailableProcessors, long jreFreeMemory,
	        String jreVersion, long maxMemory, long totalMemory, int currentRoundIndex,
	        Hash latestSolidRoundHash, int latestSolidRoundIndex, int roundStartIndex, int lastSnapshottedRoundIndex,
	        int neighbors, int packetsQueueSize,
	        long currentTimeMillis, int tips, int numberOfTransactionsToRequest, String[] features) {
		final GetNodeInfoResponse res = new GetNodeInfoResponse();
		res.appName = appName;
		res.appVersion = appVersion;
		res.jreAvailableProcessors = jreAvailableProcessors;
		res.jreFreeMemory = jreFreeMemory;
		res.jreVersion = jreVersion;

		res.jreMaxMemory = maxMemory;
		res.jreTotalMemory = totalMemory;
		res.currentRoundIndex = currentRoundIndex;

		res.latestSolidRoundHash = Hex.toHexString(latestSolidRoundHash.bytes()); // latest snapshot hash
		res.latestSolidRoundIndex = latestSolidRoundIndex; // latest snapshot index

		res.roundStartIndex = roundStartIndex; // most likely obsolete
		res.lastSnapshottedRoundIndex = lastSnapshottedRoundIndex;

		res.neighbors = neighbors;
		res.packetsQueueSize = packetsQueueSize;
		res.time = currentTimeMillis;
		res.tips = tips;
		res.transactionsToRequest = numberOfTransactionsToRequest;

		res.features = features;

		return res;
	}

	/**
	 *
	 * @return {@link #appName}
	 */
	public String getAppName() {
		return appName;
	}

	/**
	 *
	 * @return {@link #appVersion}
	 */
	public String getAppVersion() {
		return appVersion;
	}

	/**
	 *
	 * @return {@link #jreAvailableProcessors}
	 */
	public int getJreAvailableProcessors() {
		return jreAvailableProcessors;
	}

	/**
	 *
	 * @return {@link #jreFreeMemory}
	 */
	public long getJreFreeMemory() {
		return jreFreeMemory;
	}

	/**
	 *
	 * @return {@link #jreMaxMemory}
	 */
	public long getJreMaxMemory() {
		return jreMaxMemory;
	}

	/**
	 *
	 * @return {@link #jreTotalMemory}
	 */
	public long getJreTotalMemory() {
		return jreTotalMemory;
	}

	/**
	 *
	 * @return {@link #jreVersion}
	 */
	public String getJreVersion() {
		return jreVersion;
	}

	/**
	 *
	 * @return {@link #currentRoundIndex}
	 */
	public int getCurrentRoundIndex() {
		return currentRoundIndex;
	}

	/**
	 *
	 * @return {@link #latestSolidRoundHash}
	 */
	public String getLatestSolidRoundHash() {
		return latestSolidRoundHash;
	}

	/**
	 *
	 * @return {@link #latestSolidRoundIndex}
	 */
	public int getLatestSolidRoundIndex() {
		return latestSolidRoundIndex;
	}

	/**
	 *
	 * @return {@link #roundStartIndex}
	 */
	public int getRoundStartIndex() {
		return roundStartIndex;
	}

	/**
	 *
	 * @return {@link #neighbors}
	 */
	public int getNeighbors() {
		return neighbors;
	}

	/**
	 *
	 * @return {@link #packetsQueueSize}
	 */
	public int getPacketsQueueSize() {
		return packetsQueueSize;
	}

	/**
	 *
	 * @return {@link #time}
	 */
	public long getTime() {
		return time;
	}

	/**
	 *
	 * @return {@link #tips}
	 */
	public int getTips() {
		return tips;
	}

	/**
	 *
	 * @return {@link #transactionsToRequest}
	 */
	public int getTransactionsToRequest() {
		return transactionsToRequest;
	}

	/**
	 *
	 * @return {@link #features}
	 */
	public String[] getFeatures() {
		return features;
	}
}
