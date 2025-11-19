package com.example.demo.application.exception;

/**
 * Base unchecked exception for failures in the application layer.
 * Application services throw subclasses of this type to signal business validation errors without
 * coupling to the transport or persistence infrastructure.
 */
public abstract class ApplicationException extends RuntimeException {

	/**
	 * Creates a new application-layer exception with the provided message.
	 *
	 * @param message human readable error description suitable for surfacing to the caller
	 */
    protected ApplicationException(String message) {
        super(message);
    }

	/**
	 * Creates a new application-layer exception that wraps an underlying cause.
	 *
	 * @param message human readable error description suitable for surfacing to the caller
	 * @param cause   underlying exception coming from deeper layers
	 */
    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
