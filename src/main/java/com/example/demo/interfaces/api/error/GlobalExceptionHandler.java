package com.example.demo.interfaces.api.error;

import com.example.demo.application.exception.ApplicationException;
import com.example.demo.application.exception.CsvExportValidationException;
import com.example.demo.application.exception.UseCaseValidationException;
import com.example.demo.domain.exception.DomainException;
import com.example.demo.domain.exception.PdfNotFoundException;
import com.example.demo.infrastructure.exception.InfrastructureException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized API-layer exception handler that maps domain/application/infrastructure failures to HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maps {@link PdfNotFoundException} to a 404 response.
     *
     * @param ex       thrown exception
     * @param request  incoming HTTP request
     * @return response entity with serialized error payload
     */
    @ExceptionHandler(PdfNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePdfNotFound(PdfNotFoundException ex, HttpServletRequest request) {
        return buildResponse(ex, request, HttpStatus.NOT_FOUND, "PDF_NOT_FOUND");
    }

    /**
     * Maps generic domain validation exceptions to a 400 response.
     *
     * @param ex      thrown domain exception
     * @param request incoming HTTP request
     * @return response entity with serialized error payload
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex, HttpServletRequest request) {
        return buildResponse(ex, request, HttpStatus.BAD_REQUEST, "DOMAIN_ERROR");
    }

    /**
     * Maps CSV export validation exceptions to a 422 response.
     *
     * @param ex      thrown exception
     * @param request incoming HTTP request
     * @return response entity with serialized error payload
     */
    @ExceptionHandler(CsvExportValidationException.class)
    public ResponseEntity<ErrorResponse> handleCsvExportValidation(CsvExportValidationException ex, HttpServletRequest request) {
        return buildResponse(ex, request, HttpStatus.UNPROCESSABLE_ENTITY, "CSV_EXPORT_VALIDATION_ERROR");
    }

    /**
     * Maps generic use-case validation exceptions to a 400 response.
     *
     * @param ex      thrown exception
     * @param request incoming HTTP request
     * @return response entity with serialized error payload
     */
    @ExceptionHandler(UseCaseValidationException.class)
    public ResponseEntity<ErrorResponse> handleUseCaseValidation(UseCaseValidationException ex, HttpServletRequest request) {
        return buildResponse(ex, request, HttpStatus.BAD_REQUEST, "USE_CASE_VALIDATION_ERROR");
    }

    /**
     * Maps other application-layer exceptions to a 422 response.
     *
     * @param ex      thrown exception
     * @param request incoming HTTP request
     * @return response entity with serialized error payload
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplication(ApplicationException ex, HttpServletRequest request) {
        return buildResponse(ex, request, HttpStatus.UNPROCESSABLE_ENTITY, "APPLICATION_ERROR");
    }

    /**
     * Maps infrastructure exceptions to a 500 response.
     *
     * @param ex      thrown exception
     * @param request incoming HTTP request
     * @return response entity with serialized error payload
     */
    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<ErrorResponse> handleInfrastructure(InfrastructureException ex, HttpServletRequest request) {
        return buildResponse(ex, request, HttpStatus.INTERNAL_SERVER_ERROR, "INFRASTRUCTURE_ERROR");
    }

    /**
     * Fallback for unexpected exceptions.
     *
     * @param ex      thrown exception
     * @param request incoming HTTP request
     * @return response entity with serialized error payload
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        return buildResponse(ex, request, HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR");
    }

    /**
     * Central helper that creates a consistent {@link ErrorResponse} envelope.
     *
     * @param error    exception that triggered the handler
     * @param request  incoming HTTP request
     * @param status   HTTP status code to return
     * @param errorCode application-specific error code
     * @return response entity containing the serialized error
     */
    private ResponseEntity<ErrorResponse> buildResponse(Throwable error,
                                                       HttpServletRequest request,
                                                       HttpStatus status,
                                                       String errorCode) {
        ErrorResponse response = ErrorResponse.of(status.value(), errorCode, error.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(response);
    }
}
