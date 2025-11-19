package com.example.demo.domain.model;

import java.time.LocalDate;

/**
 * Domain DTO that captures metadata extracted from the Suica PDF heading region.
 * Provides context (card number, creation date) for each parsed table row.
 */
public record StatementMetadata(
        String heading,
        String cardNumberLine,
        String historySummary,
        String createdLine,
        LocalDate createdDate
) {
}
