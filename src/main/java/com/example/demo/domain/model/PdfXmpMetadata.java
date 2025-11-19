package com.example.demo.domain.model;

/**
 * Domain DTO representing the most relevant XMP metadata exposed to clients.
 * Allows the UI to surface provenance data while keeping parsing noise hidden.
 */
public record PdfXmpMetadata(
        String dublinCoreTitle,
        String dublinCoreCreators,
        String dublinCoreDates,
        String createDate,
        String creatorTool,
        String metadataDate
) {
}
