package com.example.demo.application.service;

import com.example.demo.application.exception.CsvExportValidationException;
import com.example.demo.domain.model.PdfExtractionResult;
import com.example.demo.domain.model.SuicaStatementRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests verifying the CSV export application service honors validation rules and produces CSV output.
 */
class CsvExportServiceTest {

    private final CsvExportService service = new CsvExportService();

    /**
     * Ensures validation fails when no cached result exists in the session.
     */
    @Test
    void exportSelectedRowsRequiresCachedResult() {
        assertThrows(CsvExportValidationException.class, () -> service.exportSelectedRows(null, List.of(1)));
    }

    /**
     * Ensures validation fails when row identifiers are missing.
     */
    @Test
    void exportSelectedRowsRequiresIds() {
        PdfExtractionResult result = sampleResult();

        assertThrows(CsvExportValidationException.class, () -> service.exportSelectedRows(result, null));
    }

    /**
     * Ensures the service produces CSV content for valid selections.
     */
    @Test
    void exportSelectedRowsReturnsCsv() {
        PdfExtractionResult result = sampleResult();

        String csv = service.exportSelectedRows(result, List.of(2));

        assertThat(csv).contains("Row,Year-Month");
        assertThat(csv).contains("2,2024-02,02,15");
    }

    /**
     * @return sample extraction result used across the test cases
     */
    private PdfExtractionResult sampleResult() {
        List<SuicaStatementRow> rows = List.of(
                new SuicaStatementRow(1, "2024-01", "01", "10", "IN", "StationA", "OUT", "StationB", "¥1000", "¥500"),
                new SuicaStatementRow(2, "2024-02", "02", "15", "IN", "StationC", "OUT", "StationD", "¥2000", "¥800")
        );
        return new PdfExtractionResult("sample.pdf", 2, null, rows, null, null);
    }
}
