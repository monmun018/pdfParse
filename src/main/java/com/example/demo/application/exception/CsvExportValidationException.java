package com.example.demo.application.exception;

/**
 * Dedicated exception for CSV export validation problems within the application layer.
 * Thrown when the selected table rows cannot be exported due to user mistakes.
 */
public class CsvExportValidationException extends UseCaseValidationException {

	/**
	 * Creates a new exception describing why the export request is invalid.
	 *
	 * @param message validation message suitable for display
	 */
    public CsvExportValidationException(String message) {
        super(message);
    }
}
