package com.example.demo.domain.exception;

/**
 * Raised when a caller attempts to extract text from a null {@link java.nio.file.Path}.
 * Keeps the domain layer strict about explicit file references.
 */
public class PdfPathRequiredException extends DomainException {

	/**
	 * Creates the exception with a predefined error message.
	 */
    public PdfPathRequiredException() {
        super("PDF path is required.");
    }
}
