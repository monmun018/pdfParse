package com.example.demo.domain.model;

/**
 * Domain DTO exposing a subset of the PDF info dictionary fields.
 * Populated by infrastructure readers and shipped to clients via controllers.
 */
public record PdfInfoDictionary(
        String title,
        String author,
        String subject,
        String keywords,
        String creator,
        String producer,
        String creationDate,
        String modificationDate,
        String trapped
) {
}
