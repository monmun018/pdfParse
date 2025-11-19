package com.example.demo.domain.exception;

/**
 * Raised when the uploaded file does not resemble a PDF according to the domain rules.
 * This protects the parser from receiving unsupported formats.
 */
public class UnsupportedPdfFormatException extends DomainException {

	/**
	 * Creates the exception and mentions the offending file so the user can react.
	 *
	 * @param fileName original file name supplied by the client
	 */
    public UnsupportedPdfFormatException(String fileName) {
        super("Only PDF uploads are supported" + (fileName != null ? ": " + fileName : "."));
    }
}
