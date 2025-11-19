package com.example.demo.infrastructure.exception;

/**
 * Base unchecked exception for infrastructure concerns (IO, DB, HTTP, etc.).
 * Keeps adapter failures isolated from the domain language.
 */
public abstract class InfrastructureException extends RuntimeException {

	/**
	 * Creates a new infrastructure exception while preserving the root cause.
	 *
	 * @param message context about the failure
	 * @param cause   exception bubbling up from lower level libraries
	 */
    protected InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
