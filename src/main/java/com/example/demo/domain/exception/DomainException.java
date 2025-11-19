package com.example.demo.domain.exception;

/**
 * Base type for all domain-level exceptions in the core model.
 * Subclasses capture invariant violations without leaking infrastructure dependencies.
 */
public abstract class DomainException extends RuntimeException {

	/**
	 * Creates a domain exception with a descriptive failure message.
	 *
	 * @param message explanation of which invariant broke
	 */
    protected DomainException(String message) {
        super(message);
    }

	/**
	 * Creates a domain exception that wraps an underlying cause.
	 *
	 * @param message explanation of which invariant broke
	 * @param cause   original exception that triggered the domain failure
	 */
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
