package com.example.demo.domain.exception;

/**
 * Raised when the client attempts to run an upload flow without providing a PDF file.
 * This is a domain-layer guard that protects downstream parsing logic from null inputs.
 */
public class PdfFileRequiredException extends DomainException {

	/**
	 * Creates the exception with a user-friendly explanation.
	 */
    public PdfFileRequiredException() {
        super("Please choose a PDF file to upload.");
    }
}
