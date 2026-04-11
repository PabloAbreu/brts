package org.brts.common.exception;

/**
 * Thrown when a parse operation fails (binary or JSON descriptor).
 */
public class ParseException extends BrtException {

	public ParseException(String message) {
		super(message);
	}

	public ParseException(String message, Throwable cause) {
		super(message, cause);
	}

}
