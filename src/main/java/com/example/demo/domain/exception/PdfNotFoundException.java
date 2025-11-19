package com.example.demo.domain.exception;

/**
 * Raised by domain services when a referenced PDF path does not exist on disk.
 * Prevents the application layer from reading files that have already been moved or deleted.
 */
public class PdfNotFoundException extends DomainException {

	/**
	 * Creates the exception and records the missing path as part of the message.
	 *
	 * @param path absolute or relative path that could not be resolved
	 */
    public PdfNotFoundException(String path) {
        super("PDF not found: " + path);
    }
}
