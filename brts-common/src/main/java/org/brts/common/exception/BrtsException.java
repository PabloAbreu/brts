package org.brts.common.exception;

/**
 * Base exception for all BRT tool errors.
 */
public class BrtsException extends RuntimeException {

	public BrtsException(String message) {
		super(message);
	}

	public BrtsException(String message, Throwable cause) {
		super(message, cause);
	}

}
