package com.example.demo.domain.model;

import java.util.List;

/**
 * Domain DTO containing the metadata and extracted text of an uploaded PDF.
 * Returned from {@code PdfTextService} to controllers so UI code can be kept presentation-only.
 */
public record PdfExtractionResult(
        String fileName,
        int pageCount,
        StatementMetadata metadata,
        List<SuicaStatementRow> rows,
        String extractedText,
        PdfDocumentMetadata documentMetadata
) {
}
