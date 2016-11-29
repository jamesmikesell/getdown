package com.threerings.getdown.data;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;

import com.threerings.getdown.util.ProgressObserver;

/**
 * Models a single file resource used by an {@link Application}.
 */
public interface Resource
{
	/**
	 * Returns the path associated with this resource.
	 */
	String getPath();

	/**
	 * Returns the local location of this resource.
	 */
	File getLocal();

	/**
	 *  Returns the final target of this resource, whether it has been unpacked or not.
	 */
	File getFinalTarget();

	/**
	 * Returns the remote location of this resource.
	 */
	URL getRemote();

	/**
	 * Returns true if this resource should be unpacked as a part of the
	 * validation process.
	 */
	boolean shouldUnpack();

	/**
	 * Computes the MD5 hash of this resource's underlying file.
	 * <em>Note:</em> This is both CPU and I/O intensive.
	 */
	String computeDigest(MessageDigest md, ProgressObserver obs) throws IOException;

	/**
	 * Returns true if this resource has an associated "validated" marker
	 * file.
	 */
	boolean isMarkedValid();

	/**
	 * Creates a "validated" marker file for this resource to indicate
	 * that its MD5 hash has been computed and compared with the value in
	 * the digest file.
	 *
	 * @throws IOException if we fail to create the marker file.
	 */
	void markAsValid() throws IOException;

	/**
	 * Removes any "validated" marker file associated with this resource.
	 */
	void clearMarker();

	/**
	 * Unpacks this resource file into the directory that contains it. Returns
	 * false if an error occurs while unpacking it.
	 */
	boolean unpack();

	boolean equals(Object other);

	int hashCode();

	String toString();
}
