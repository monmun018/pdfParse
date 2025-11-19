package com.example.demo.application.service;

import com.example.demo.application.exception.CsvExportValidationException;
import com.example.demo.domain.model.PdfExtractionResult;
import com.example.demo.domain.model.SuicaStatementRow;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application-layer service that turns parsed statement rows into downloadable CSV content.
 */
@Service
public class CsvExportService {

	/**
	 * Runs validation and returns a CSV string containing the requested rows.
	 *
	 * @param extractionResult cached extraction result stored in the session
	 * @param rowIds           indices selected on the UI
	 * @return CSV content ready to stream to the browser
	 * @throws CsvExportValidationException when no rows or IDs are available
	 */
    public String exportSelectedRows(PdfExtractionResult extractionResult, List<Integer> rowIds) {
        if (extractionResult == null || extractionResult.rows() == null || extractionResult.rows().isEmpty()) {
            throw new CsvExportValidationException("No parsed rows available for export.");
        }
        if (rowIds == null || rowIds.isEmpty()) {
            throw new CsvExportValidationException("Please select at least one row before exporting.");
        }

        List<SuicaStatementRow> selected = extractionResult.rows().stream()
                .filter(row -> rowIds.contains(row.rowNumber()))
                .toList();
        if (selected.isEmpty()) {
            throw new CsvExportValidationException("Selected rows were not found.");
        }

        return buildCsv(selected);
    }

	/**
	 * Builds the CSV output including the header row and sanitized values.
	 *
	 * @param rows selected statement rows
	 * @return CSV document as a string
	 */
    private String buildCsv(List<SuicaStatementRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("Row,Year-Month,Month,Day,Type (In),Station (In),Type (Out),Station (Out),Balance,Amount\n");
        for (SuicaStatementRow row : rows) {
            builder.append(row.rowNumber()).append(',')
                    .append(escape(row.yearMonth())).append(',')
                    .append(escape(row.month())).append(',')
                    .append(escape(row.day())).append(',')
                    .append(escape(row.typeIn())).append(',')
                    .append(escape(row.stationIn())).append(',')
                    .append(escape(row.typeOut())).append(',')
                    .append(escape(row.stationOut())).append(',')
                    .append(escape(row.balance())).append(',')
                    .append(escape(row.amount()))
                    .append('\n');
        }
        return builder.toString();
    }

	/**
	 * Escapes CSV values by quoting entries containing commas, quotes, or newlines.
	 *
	 * @param value raw column value
	 * @return sanitized CSV-safe token
	 */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\"", "\"\"");
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n")) {
            return "\"" + sanitized + "\"";
        }
        return sanitized;
    }
}
