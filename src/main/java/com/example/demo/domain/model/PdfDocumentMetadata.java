package com.example.demo.domain.model;

/**
 * Aggregated domain DTO that surfaces PDF-level metadata to the interfaces layer.
 * Constructed by infrastructure readers and consumed in the application/domain boundary.
 */
public record PdfDocumentMetadata(
        PdfInfoDictionary infoDictionary,
        PdfXmpMetadata xmpMetadata,
        int pageCount,
        String pdfVersion,
        boolean encrypted,
        long fileSizeBytes
) {
}
