package org.brts.common.exception;

/**
 * Thrown when a write / generation operation fails.
 */
public class WriteException extends BrtException {

	public WriteException(String message) {
		super(message);
	}

	public WriteException(String message, Throwable cause) {
		super(message, cause);
	}

}
