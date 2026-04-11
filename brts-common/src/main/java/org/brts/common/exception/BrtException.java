package org.brts.common.exception;

/**
 * Base exception for all BRT tool errors.
 */
public class BrtException extends RuntimeException {

	public BrtException(String message) {
		super(message);
	}

	public BrtException(String message, Throwable cause) {
		super(message, cause);
	}

}
