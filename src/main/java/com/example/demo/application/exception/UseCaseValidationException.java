package com.example.demo.application.exception;

/**
 * Signals validation issues detected while running an application layer use case.
 * Controllers may translate this exception into HTTP 400/422 responses depending on context.
 */
public class UseCaseValidationException extends ApplicationException {

	/**
	 * Builds an exception containing a validation message that can be propagated to the UI.
	 *
	 * @param message specific validation failure
	 */
    public UseCaseValidationException(String message) {
        super(message);
    }
}
