package net.helix.sbx.service.dto;

import java.util.List;

import net.helix.sbx.service.API;

/**
 *
 * Contains information about the result of a successful {@code attachToTangle} API call.
 * @see {@link API#attachToTangleStatement} for how this response is created.
 *
 */
public class AttachToTangleResponse extends AbstractResponse {

	/**
	 * List of the attached transaction bytes.
	 * The last 96 bytes of the return value consist of the:
	 * <code>trunkTransaction</code> + <code>branchTransaction</code> + <code>nonce</code>.
	 */
	private List<String> hbytes;

	/**
	 * Creates a new {@link AttachToTangleResponse}
	 * @param elements {@link #hbytes}
	 * @return an {@link AttachToTangleResponse} filled with the hbytes
	 */
	public static AbstractResponse create(List<String> elements) {
		AttachToTangleResponse res = new AttachToTangleResponse();
		res.hbytes = elements;
		return res;
	}

	/**
	 *
	 * @return {@link #hbytes}
	 */
	public List<String> getHBytes() {
		return hbytes;
	}
}
