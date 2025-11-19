package com.example.demo.infrastructure.exception;

/**
 * Infrastructure-layer exception that signals issues while reading a PDF from disk or memory.
 */
public class PdfProcessingException extends InfrastructureException {
	/**
	 * Creates the exception with a contextual message and the root cause from PDFBox.
	 *
	 * @param message description shared with the application layer
	 * @param cause   low-level PDFBox exception
	 */
    public PdfProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
