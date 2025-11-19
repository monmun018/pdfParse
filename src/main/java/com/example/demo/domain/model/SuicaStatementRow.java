package com.example.demo.domain.model;

/**
 * Domain DTO describing a parsed table row from the Suica transaction statement.
 * Each field maps to the PDF columns so CSV export and UI rendering can remain trivial.
 */
public record SuicaStatementRow(
        int rowNumber,
        String yearMonth,
        String month,
        String day,
        String typeIn,
        String stationIn,
        String typeOut,
        String stationOut,
        String balance,
        String amount
) {
}
