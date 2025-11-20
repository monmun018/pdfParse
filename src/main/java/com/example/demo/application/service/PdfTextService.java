package com.example.demo.application.service;

import com.example.demo.domain.exception.PdfFileRequiredException;
import com.example.demo.domain.exception.PdfNotFoundException;
import com.example.demo.domain.exception.PdfPathRequiredException;
import com.example.demo.domain.exception.UnsupportedPdfFormatException;
import com.example.demo.domain.model.PdfDocumentMetadata;
import com.example.demo.domain.model.PdfExtractionResult;
import com.example.demo.domain.model.PdfFeature;
import com.example.demo.domain.model.StatementMetadata;
import com.example.demo.domain.model.SuicaStatementRow;
import com.example.demo.domain.model.SuicaPdfType;
import com.example.demo.infrastructure.exception.PdfProcessingException;
import com.example.demo.infrastructure.pdf.PdfBoxMetadataReader;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application-layer service that orchestrates Suica PDF parsing.
 * It validates inputs, delegates PDFBox interactions to infrastructure helpers, and returns rich domain DTOs.
 */
@Service
public class PdfTextService {

    private static final Logger log = LoggerFactory.getLogger(PdfTextService.class);
    private static final Pattern CARD_PATTERN = Pattern.compile("JE[\\d*\\s]+\\d{4}");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}/\\d{2}/\\d{2})");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> ENTRY_ONLY_TYPES = Set.of("ｶｰﾄﾞ", "カード", "ﾓﾊﾞｲﾙ", "モバイル", "物販");
    private static final int FULL_COLUMN_COUNT = 8;
    private static final int PARTIAL_COLUMN_COUNT = 7;
    private static final List<ColumnDefinition> FULL_COLUMN_DEFINITIONS = List.of(
            new ColumnDefinition("month", List.of("月")),
            new ColumnDefinition("day", List.of("日")),
            new ColumnDefinition("typeIn", List.of("種別")),
            new ColumnDefinition("stationIn", List.of("利用駅")),
            new ColumnDefinition("typeOut", List.of("種別")),
            new ColumnDefinition("stationOut", List.of("利用駅")),
            new ColumnDefinition("balance", List.of("残高")),
            new ColumnDefinition("amount", List.of("入金", "利用金額"))
    );
    private static final List<ColumnDefinition> PARTIAL_COLUMN_DEFINITIONS = List.of(
            new ColumnDefinition("month", List.of("月")),
            new ColumnDefinition("day", List.of("日")),
            new ColumnDefinition("typeIn", List.of("種別")),
            new ColumnDefinition("stationIn", List.of("利用駅")),
            new ColumnDefinition("typeOut", List.of("種別")),
            new ColumnDefinition("stationOut", List.of("利用駅")),
            new ColumnDefinition("amount", List.of("入金", "利用金額"))
    );

    private final PdfBoxMetadataReader metadataReader;

    /**
     * Creates the service with the infrastructure metadata reader dependency.
     *
     * @param metadataReader helper responsible for turning PDFBox metadata into domain DTOs
     */
    public PdfTextService(PdfBoxMetadataReader metadataReader) {
        this.metadataReader = metadataReader;
    }

    /**
     * Extracts every feature from the uploaded {@link MultipartFile}.
     *
     * @param file uploaded PDF file
     * @return extraction result containing metadata, rows, and text
     */
    public PdfExtractionResult extractText(MultipartFile file) {
        return extractText(file, PdfFeature.allFeatures());
    }

    /**
     * Extracts only the requested features from the uploaded PDF file.
     *
     * @param file     uploaded file
     * @param features subset of {@link PdfFeature} to compute
     * @return extraction result containing only the requested payloads
     * @throws PdfFileRequiredException         when the file is null or empty
     * @throws UnsupportedPdfFormatException    when the MIME type/name does not look like a PDF
     * @throws PdfProcessingException           when PDFBox cannot read the bytes
     */
    public PdfExtractionResult extractText(MultipartFile file, Set<PdfFeature> features) {
        if (file == null || file.isEmpty()) {
            throw new PdfFileRequiredException();
        }
        if (!looksLikePdf(file)) {
            throw new UnsupportedPdfFormatException(file.getOriginalFilename());
        }

        try {
            return extractTextInternal(file.getBytes(), resolveFileName(file), normalizeFeatures(features));
        } catch (IOException e) {
            throw new PdfProcessingException("Unable to process the uploaded PDF file.", e);
        }
    }

    /**
     * Reads a PDF from the filesystem and extracts every available feature.
     *
     * @param pdfPath path pointing to a PDF file on disk
     * @return extraction result with metadata, rows, and text
     */
    public PdfExtractionResult extractText(Path pdfPath) {
        return extractText(pdfPath, PdfFeature.allFeatures());
    }

    /**
     * Reads a PDF from the filesystem and extracts the requested features.
     *
     * @param pdfPath  path pointing to a PDF file on disk
     * @param features subset of {@link PdfFeature}
     * @return extraction result containing only the requested payloads
     * @throws PdfPathRequiredException when {@code pdfPath} is null
     * @throws PdfNotFoundException     when the path does not exist
     * @throws PdfProcessingException   when the file cannot be read
     */
    public PdfExtractionResult extractText(Path pdfPath, Set<PdfFeature> features) {
        if (pdfPath == null) {
            throw new PdfPathRequiredException();
        }
        if (!Files.exists(pdfPath)) {
            throw new PdfNotFoundException(pdfPath.toAbsolutePath().toString());
        }
        try {
            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            String fileName = pdfPath.getFileName() != null ? pdfPath.getFileName().toString() : "sample.pdf";
            return extractTextInternal(pdfBytes, fileName, normalizeFeatures(features));
        } catch (IOException e) {
            throw new PdfProcessingException("Unable to process the PDF at " + pdfPath, e);
        }
    }

    /**
     * Normalizes the incoming feature collection into a mutable {@link EnumSet}.
     *
     * @param features caller-supplied feature list
     * @return clone to avoid mutating inputs or {@link PdfFeature#allFeatures()} when empty
     */
    private EnumSet<PdfFeature> normalizeFeatures(Set<PdfFeature> features) {
        if (features == null || features.isEmpty()) {
            return PdfFeature.allFeatures();
        }
        return features instanceof EnumSet<PdfFeature> enumSet
                ? enumSet.clone()
                : EnumSet.copyOf(features);
    }

    /**
     * Shared implementation for parsing PDFs regardless of the request source.
     *
     * @param bytes     PDF bytes already loaded into memory
     * @param fileName  logical name used for display purposes
     * @param features  normalized set of requested features
     * @return populated extraction result
     * @throws IOException propagated when {@link Loader#loadPDF(byte[])} fails
     */
    private PdfExtractionResult extractTextInternal(byte[] bytes, String fileName, EnumSet<PdfFeature> features) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String rawText = null;
            StatementMetadata metadata = null;
            List<SuicaStatementRow> rows = Collections.emptyList();
            SuicaPdfType pdfType = SuicaPdfType.FULL_HISTORY;

            boolean needsDocumentText = features.contains(PdfFeature.RAW_TEXT) || features.contains(PdfFeature.STATEMENT_METADATA);
            if (needsDocumentText) {
                rawText = extractFullDocumentText(document);
            }
            if (features.contains(PdfFeature.STATEMENT_METADATA)) {
                metadata = extractMetadata(rawText);
            }
            if (features.contains(PdfFeature.TABLE_ROWS)) {
                List<TableLine> tableLines = extractStatementTable(document);
                TableParseResult tableParse = parseTableLines(tableLines, metadata);
                rows = tableParse.rows();
                pdfType = tableParse.pdfType();
            }

            return new PdfExtractionResult(
                    fileName,
                    document.getNumberOfPages(),
                    metadata,
                    rows,
                    features.contains(PdfFeature.RAW_TEXT) ? rawText : null,
                    features.contains(PdfFeature.DOCUMENT_METADATA)
                            ? metadataReader.readMetadata(document, bytes.length)
                            : null,
                    pdfType
            );
        }
    }

    /**
     * Uses {@link PDFTextStripper} to extract and normalize all text on each page.
     *
     * @param document loaded PDF document
     * @return stripped text used for metadata parsing
     * @throws IOException when PDFBox cannot read the page content
     */
    private String extractFullDocumentText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        configureStripper(stripper);
        stripper.setLineSeparator("\n");
        stripper.setWordSeparator(" | ");
        stripper.setParagraphEnd("\n");
        stripper.setAddMoreFormatting(true);
        stripper.setSortByPosition(true);
        stripper.setShouldSeparateByBeads(true);
        stripper.setAverageCharTolerance(0.12f);
        stripper.setSpacingTolerance(0.2f);
        return stripper.getText(document).strip();
    }

    /**
     * Parses the free-form heading text and extracts statement metadata fields.
     *
     * @param documentText aggregated text from the PDF
     * @return structured metadata describing the statement heading
     */
    private StatementMetadata extractMetadata(String documentText) {
        List<String> lines = documentText.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();

        String heading = findFirstMatch(lines, line -> line.contains("モバイル") && line.contains("残高ご利用明細"));
        String cardLine = findFirstMatch(lines, line -> CARD_PATTERN.matcher(line).find());
        String historySummary = findFirstMatch(lines, line -> line.contains("残高履歴"));
        String createdLine = findFirstMatch(lines, line -> line.contains("ご利用ありがとうございます") || DATE_PATTERN.matcher(line).find());
        LocalDate createdDate = extractCreatedDate(lines);

        return new StatementMetadata(heading, cardLine, historySummary, createdLine, createdDate);
    }

    /**
     * Attempts to read the creation date from the heading lines.
     *
     * @param lines normalized document lines
     * @return parsed {@link LocalDate} or {@code null}
     */
    private LocalDate extractCreatedDate(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = DATE_PATTERN.matcher(line);
            if (matcher.find()) {
                try {
                    return LocalDate.parse(matcher.group(1), DATE_FORMATTER);
                } catch (DateTimeParseException ignored) {
                    // fall through
                }
            }
        }
        return null;
    }

    /**
     * Extracts the statement table text by selecting a bounding box on every page.
     *
     * @param document loaded PDF document
     * @return concatenated table text spanning all pages
     * @throws IOException when PDFBox cannot read the table region
     */
    private List<TableLine> extractStatementTable(PDDocument document) throws IOException {
        List<TableLine> lines = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            PDPage page = document.getPage(pageIndex);
            Float headerY = detectTableHeaderY(document, pageIndex);
            Rectangle2D region = resolveTableRegionForPage(pageIndex, page, headerY);
            if (region == null) {
                continue;
            }

            TableRegionStripper stripper = new TableRegionStripper(region);
            configureStripper(stripper);
            stripper.setLineSeparator("\n");
            stripper.setWordSeparator(" ");
            stripper.setParagraphEnd("\n");
            stripper.setAddMoreFormatting(true);
            stripper.setAverageCharTolerance(0.12f);
            stripper.setSpacingTolerance(0.2f);
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            stripper.getText(document);
            lines.addAll(stripper.getLines());
        }
        return lines;
    }

    /**
     * Converts the extracted table text into domain rows.
     *
     * @param tableLines parsed lines with positional information
     * @param metadata  statement metadata used for contextual fields
     * @return parsed row list (possibly empty)
     */
    TableParseResult parseTableLines(List<TableLine> tableLines, StatementMetadata metadata) {
        if (tableLines == null || tableLines.isEmpty()) {
            return new TableParseResult(Collections.emptyList(), SuicaPdfType.FULL_HISTORY);
        }

        TableLine headerLine = findHeaderLine(tableLines);
        SuicaPdfType pdfType = detectPdfType(headerLine, tableLines);
        List<ColumnDefinition> definitions = columnDefinitionsFor(pdfType);
        ColumnLayout columnLayout = ColumnLayout.build(headerLine, tableLines, definitions, log);
        if (columnLayout == null) {
            log.warn("Unable to determine column layout for the statement table.");
            return new TableParseResult(Collections.emptyList(), pdfType);
        }

        List<SuicaStatementRow> rows = new ArrayList<>();
        int rowNumber = 1;
        boolean headerSeen = false;
        for (TableLine tableLine : tableLines) {
            String line = tableLine.text();
            if (line.isEmpty()) {
                continue;
            }
            if (!headerSeen) {
                if (isHeaderLine(line)) {
                    headerSeen = true;
                }
                if (!headerSeen && looksLikeDataLine(line)) {
                    headerSeen = true;
                } else if (!headerSeen) {
                    continue;
                }
            }
            if (isHeaderLine(line)) {
                continue;
            }
            if (isFooterLine(line)) {
                continue;
            }
            List<String> columns = columnLayout.extractColumns(tableLine, this::cleanToken);
            if (columns.size() != definitions.size()) {
                continue;
            }
            SuicaStatementRow row = toRow(columns, rowNumber, metadata, pdfType);
            if (row != null) {
                rows.add(row);
                rowNumber++;
            }
        }
        return new TableParseResult(rows, pdfType);
    }

    static final class TableParseResult {
        private final List<SuicaStatementRow> rows;
        private final SuicaPdfType pdfType;

        TableParseResult(List<SuicaStatementRow> rows, SuicaPdfType pdfType) {
            this.rows = rows == null ? Collections.emptyList() : rows;
            this.pdfType = pdfType == null ? SuicaPdfType.FULL_HISTORY : pdfType;
        }

        List<SuicaStatementRow> rows() {
            return rows;
        }

        SuicaPdfType pdfType() {
            return pdfType;
        }
    }

    /**
     * Locates the first table line that matches the expected header structure.
     *
     * @param lines extracted table lines
     * @return header line or {@code null} when undetected
     */
    private TableLine findHeaderLine(List<TableLine> lines) {
        if (lines == null) {
            return null;
        }
        for (TableLine line : lines) {
            if (line == null) {
                continue;
            }
            String text = line.text();
            if (text != null && isHeaderLine(text)) {
                return line;
            }
        }
        return null;
    }

    /**
     * Determines the PDF type (full history vs partial selection) based on header keywords and token counts.
     *
     * @param headerLine detected header line (may be null)
     * @param lines      all table lines
     * @return inferred PDF type
     */
    private SuicaPdfType detectPdfType(TableLine headerLine, List<TableLine> lines) {
        boolean headerHasBalance = false;
        boolean headerHasAmount = false;
        if (headerLine != null) {
            for (PositionedToken token : headerLine.tokens()) {
                String normalized = normalizeHeaderToken(token.text());
                if (normalized.contains("残高")) {
                    headerHasBalance = true;
                }
                if (normalized.contains("入金利用金額") || normalized.contains("入金利用額") || normalized.contains("入金利用金")) {
                    headerHasAmount = true;
                }
            }
        }
        if (headerHasAmount && !headerHasBalance) {
            return SuicaPdfType.PARTIAL_SELECTION;
        }
        if (headerHasBalance) {
            return SuicaPdfType.FULL_HISTORY;
        }

        int maxTokenCount = lines == null
                ? 0
                : lines.stream()
                .filter(Objects::nonNull)
                .mapToInt(line -> line.tokens().size())
                .max()
                .orElse(0);
        if (maxTokenCount > 0 && maxTokenCount <= PARTIAL_COLUMN_COUNT) {
            return SuicaPdfType.PARTIAL_SELECTION;
        }

        return SuicaPdfType.FULL_HISTORY;
    }

    private List<ColumnDefinition> columnDefinitionsFor(SuicaPdfType pdfType) {
        return pdfType == SuicaPdfType.PARTIAL_SELECTION ? PARTIAL_COLUMN_DEFINITIONS : FULL_COLUMN_DEFINITIONS;
    }

    /**
     * Checks if a line matches the table header signature.
     *
     * @param line line to inspect
     * @return {@code true} when the line is part of the header row
     */
    private boolean isHeaderLine(String line) {
        return isHeaderText(line);
    }

    /**
     * Checks if a line belongs to the footer area that should be ignored.
     *
     * @param line line to inspect
     * @return {@code true} for known footer lines
     */
    private boolean isFooterLine(String line) {
        return line.contains("ご利用ありがとうございます")
                || line.contains("システムの都合上")
                || line.contains("東日本旅客鉄道株式会社")
                || line.matches(".*\\(\\d+/\\d+\\).*")
                || DATE_PATTERN.matcher(line).find();
    }

    /**
     * Normalizes a token by trimming whitespace and replacing stray backslashes with Yen symbols.
     *
     * @param token raw token
     * @return cleaned value or empty string
     */
    private String cleanToken(String token) {
        if (token == null) {
            return "";
        }
        return token.replace("\\", "¥").trim();
    }

    /**
     * Parses a single transaction row. Supports both full 8-column layout and the
     * compact 6-column variant (no explicit 出 columns). When fallback mapping kicks in
     * we keep exit columns empty and log for visibility.
     *
     * @param columns        normalized column values
     * @param rowNumber      sequential row counter used for display
     * @param metadata       heading metadata required to compute the year-month column
     * @return parsed row or {@code null} when the tokens do not look like a data row
     */
    private SuicaStatementRow toRow(List<String> columns, int rowNumber, StatementMetadata metadata, SuicaPdfType pdfType) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }

        String month = getColumn(columns, 0);
        String day = getColumn(columns, 1);
        if ((month == null || month.isBlank()) && (day == null || day.isBlank())) {
            return null;
        }

        String typeIn = getColumn(columns, 2);
        String stationIn = getColumn(columns, 3);
        String typeOut = getColumn(columns, 4);
        String stationOut = getColumn(columns, 5);
        String balance = pdfType.hasBalanceColumn() ? normalizeCurrency(getColumn(columns, 6), true) : null;
        int amountIndex = pdfType.hasBalanceColumn() ? 7 : 6;
        String amount = normalizeCurrency(getColumn(columns, amountIndex), false);

        if (ENTRY_ONLY_TYPES.contains(typeIn)) {
            typeOut = "";
            stationOut = "";
        }

        if (stationIn != null && looksLikeExitType(stationIn) && typeOut.isEmpty()) {
            // stationIn actually belongs to exit columns when inbound station missing.
            typeOut = stationIn;
            stationIn = "";
        }

        String yearMonth = buildYearMonth(month, metadata);

        return new SuicaStatementRow(
                rowNumber,
                yearMonth,
                month,
                day,
                typeIn,
                stationIn,
                typeOut,
                stationOut,
                balance,
                amount
        );
    }

    private String getColumn(List<String> columns, int index) {
        if (index >= columns.size() || index < 0) {
            return "";
        }
        String value = columns.get(index);
        return value == null ? "" : value.trim();
    }

    /**
     * Converts a token into a standardized currency string.
     *
     * @param token     raw currency token
     * @param prefixYen whether to enforce a leading Yen symbol
     * @return normalized currency string
     */
    private String normalizeCurrency(String token, boolean prefixYen) {
        if (token == null) {
            return "";
        }
        String cleaned = token.replace("\\", "¥").trim();
        if (prefixYen && !cleaned.startsWith("¥")) {
            cleaned = "¥" + cleaned.replace("¥", "");
        }
        return cleaned;
    }

    /**
     * Heuristically checks whether a token refers to an exit indicator.
     *
     * @param token token to evaluate
     * @return {@code true} when the token contains exit-related characters
     */
    private boolean looksLikeExitType(String token) {
        if (token == null) {
            return false;
        }
        return token.contains("出") || token.contains("降");
    }

    /**
     * Checks if the line contains month/day columns indicating a transaction row.
     *
     * @param line normalized table line
     * @return {@code true} when the line resembles a data row
     */
    private boolean looksLikeDataLine(String line) {
        return line.matches(".*\\d{1,2}[\\s　]+\\d{1,2}.*");
    }

    /**
     * Derives the year-month column based on the metadata heading.
     *
     * @param monthValue month token from the line
     * @param metadata   extracted statement metadata (may contain a creation date)
     * @return formatted year-month or an empty string when unavailable
     */
    private String buildYearMonth(String monthValue, StatementMetadata metadata) {
        if (monthValue == null || monthValue.isBlank()) {
            return "";
        }
        String normalized = monthValue.replaceAll("[^0-9]", "");
        if (normalized.isEmpty()) {
            return monthValue;
        }
        try {
            int month = Integer.parseInt(normalized);
            if (metadata != null && metadata.createdDate() != null) {
                return String.format("%04d-%02d", metadata.createdDate().getYear(), month);
            }
            return String.format("%02d", month);
        } catch (NumberFormatException ignored) {
            return monthValue;
        }
    }

    /**
     * Finds the first line matching the supplied predicate.
     *
     * @param lines     normalized lines
     * @param predicate condition to test
     * @return first match or {@code null}
     */
    private String findFirstMatch(List<String> lines, java.util.function.Predicate<String> predicate) {
        return lines.stream().filter(predicate).findFirst().orElse(null);
    }

    /**
     * Applies common PDFBox stripper configuration shared by all extraction routines.
     *
     * @param stripper stripper to configure
     */
    private void configureStripper(PDFTextStripper stripper) {
        stripper.setSortByPosition(true);
        stripper.setShouldSeparateByBeads(true);
        stripper.setSuppressDuplicateOverlappingText(false);
    }

    /**
     * Normalizes header tokens by stripping whitespace and punctuation for easier matching.
     *
     * @param token raw header token
     * @return normalized token string
     */
    private static String normalizeHeaderToken(String token) {
        if (token == null) {
            return "";
        }
        return token.replaceAll("[\\s　()（）・・／/\\-]", "");
    }

    /**
     * Groups table lines and maps tokens onto the eight logical columns based on header anchors.
     */
    private static final class ColumnLayout {
        private static final float PADDING = 2f;
        private final List<ColumnBoundary> boundaries;
        private final int columnCount;

        private ColumnLayout(List<ColumnBoundary> boundaries, int columnCount) {
            this.boundaries = boundaries;
            this.columnCount = columnCount;
        }

        static ColumnLayout build(TableLine headerLine, List<TableLine> lines, List<ColumnDefinition> definitions, Logger log) {
            int columnCount = definitions.size();
            float minX = computeMinX(lines, headerLine) - PADDING;
            float maxX = computeMaxX(lines, headerLine, columnCount) + PADDING;
            if (maxX <= minX) {
                maxX = minX + columnCount;
            }

            List<Float> anchors = headerLine != null ? detectAnchors(headerLine, definitions) : Collections.emptyList();
            if (headerLine != null && anchors.size() == columnCount) {
                return new ColumnLayout(toBoundaries(anchors, minX, maxX, columnCount), columnCount);
            }

            if (headerLine == null) {
                log.warn("Table header text not detected; falling back to evenly spaced columns.");
            } else {
                log.warn("Detected only {} header anchors; falling back to evenly spaced columns.", anchors.size());
            }
            return new ColumnLayout(buildEvenBoundaries(minX, maxX, columnCount), columnCount);
        }

        List<String> extractColumns(TableLine line, Function<String, String> cleaner) {
            List<StringBuilder> builders = new ArrayList<>(columnCount);
            for (int i = 0; i < columnCount; i++) {
                builders.add(new StringBuilder());
            }
            for (PositionedToken token : line.tokens()) {
                int columnIndex = locateColumn(token.center());
                if (columnIndex < 0) {
                    continue;
                }
                String raw = token.text().strip();
                if (raw.isEmpty()) {
                    continue;
                }
                StringBuilder builder = builders.get(columnIndex);
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(raw);
            }
            List<String> values = new ArrayList<>(columnCount);
            for (StringBuilder builder : builders) {
                values.add(cleaner.apply(builder.toString()));
            }
            return values;
        }

        private int locateColumn(float center) {
            for (int i = 0; i < boundaries.size(); i++) {
                ColumnBoundary boundary = boundaries.get(i);
                if (boundary.contains(center, i == boundaries.size() - 1)) {
                    return i;
                }
            }
            return -1;
        }

        private static List<Float> detectAnchors(TableLine headerLine, List<ColumnDefinition> definitions) {
            int columnCount = definitions.size();
            List<Float> anchors = new ArrayList<>(columnCount);
            List<PositionedToken> tokens = headerLine.tokens();
            int tokenIndex = 0;
            for (ColumnDefinition definition : definitions) {
                Float anchor = null;
                for (int i = tokenIndex; i < tokens.size(); i++) {
                    String normalized = normalizeHeaderToken(tokens.get(i).text());
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    if (definition.matches(normalized)) {
                        anchor = tokens.get(i).center();
                        tokenIndex = i + 1;
                        break;
                    }
                }
                if (anchor == null) {
                    break;
                }
                anchors.add(anchor);
            }
            return anchors;
        }

        private static List<ColumnBoundary> toBoundaries(List<Float> anchors, float minX, float maxX, int columnCount) {
            List<ColumnBoundary> boundaries = new ArrayList<>(columnCount);
            for (int i = 0; i < columnCount; i++) {
                float start = i == 0 ? minX : midpoint(anchors.get(i - 1), anchors.get(i));
                float end = i == columnCount - 1 ? maxX : midpoint(anchors.get(i), anchors.get(i + 1));
                boundaries.add(new ColumnBoundary(start, end));
            }
            return boundaries;
        }

        private static List<ColumnBoundary> buildEvenBoundaries(float minX, float maxX, int columnCount) {
            List<ColumnBoundary> boundaries = new ArrayList<>(columnCount);
            float width = Math.max(maxX - minX, columnCount);
            float columnWidth = width / columnCount;
            float start = minX;
            for (int i = 0; i < columnCount; i++) {
                float end = i == columnCount - 1 ? maxX : start + columnWidth;
                boundaries.add(new ColumnBoundary(start, end));
                start = end;
            }
            return boundaries;
        }

        private static float computeMinX(List<TableLine> lines, TableLine headerLine) {
            float min = headerLine != null ? headerLine.minX() : Float.MAX_VALUE;
            for (TableLine line : lines) {
                min = Math.min(min, line.minX());
            }
            if (min == Float.MAX_VALUE) {
                return 0f;
            }
            return min;
        }

        private static float computeMaxX(List<TableLine> lines, TableLine headerLine, int columnCount) {
            float max = headerLine != null ? headerLine.maxX() : Float.MIN_VALUE;
            for (TableLine line : lines) {
                max = Math.max(max, line.maxX());
            }
            if (max == Float.MIN_VALUE) {
                return columnCount;
            }
            return max;
        }

        private static float midpoint(float left, float right) {
            return left + ((right - left) / 2f);
        }
    }

    /**
     * Defines a closed interval for a single statement column.
     */
    private static final class ColumnBoundary {
        private static final float TOLERANCE = 0.5f;
        private final float start;
        private final float end;

        ColumnBoundary(float start, float end) {
            this.start = Math.min(start, end);
            this.end = Math.max(start, end);
        }

        boolean contains(float value, boolean last) {
            if (last) {
                return value >= start - TOLERANCE && value <= end + TOLERANCE;
            }
            return value >= start - TOLERANCE && value < end - TOLERANCE;
        }
    }

    /**
     * Lightweight descriptor of one table column label with matching keywords.
     */
    private static final class ColumnDefinition {
        private final String name;
        private final List<String> keywords;

        ColumnDefinition(String name, List<String> keywords) {
            this.name = name;
            this.keywords = keywords;
        }

        boolean matches(String normalizedToken) {
            for (String keyword : keywords) {
                if (normalizedToken.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Represents a single extracted table line with ordered positioned tokens.
     */
    static final class TableLine {
        private final float y;
        private final List<PositionedToken> tokens = new ArrayList<>();
        private boolean sorted = false;

        TableLine(float y) {
            this.y = y;
        }

        void addToken(PositionedToken token) {
            if (token == null) {
                return;
            }
            tokens.add(token);
            sorted = false;
        }

        List<PositionedToken> tokens() {
            if (!sorted) {
                tokens.sort(Comparator.comparing(PositionedToken::x));
                sorted = true;
            }
            return tokens;
        }

        float minX() {
            return tokens().stream()
                    .map(PositionedToken::x)
                    .min(Float::compareTo)
                    .orElse(Float.MAX_VALUE);
        }

        float maxX() {
            return tokens().stream()
                    .map(PositionedToken::endX)
                    .max(Float::compareTo)
                    .orElse(Float.MIN_VALUE);
        }

        float y() {
            return y;
        }

        String text() {
            String joined = tokens().stream()
                    .map(PositionedToken::text)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.joining(" "));
            return joined.trim();
        }
    }

    /**
     * Token extracted from the PDF with positional metadata.
     */
    static final class PositionedToken {
        private final float x;
        private final float endX;
        private final String text;

        PositionedToken(float x, float endX, String text) {
            this.x = x;
            this.endX = Math.max(endX, x);
            this.text = text == null ? "" : text;
        }

        float x() {
            return x;
        }

        float endX() {
            return endX;
        }

        float center() {
            return x + ((endX - x) / 2f);
        }

        String text() {
            return text;
        }
    }

    /**
     * Strips text from a rectangular region while preserving token positions for column alignment.
     */
    private static final class TableRegionStripper extends PDFTextStripper {
        private static final float Y_TOLERANCE = 1.5f;
        private final Rectangle2D region;
        private final List<TableLine> lines = new ArrayList<>();

        TableRegionStripper(Rectangle2D region) throws IOException {
            this.region = region;
        }

        List<TableLine> getLines() {
            lines.sort(Comparator.comparing(TableLine::y));
            return new ArrayList<>(lines);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            List<TextPosition> filtered = new ArrayList<>();
            for (TextPosition position : textPositions) {
                float x = position.getXDirAdj();
                float y = position.getYDirAdj();
                if (region.contains(x, y)) {
                    filtered.add(position);
                }
            }
            if (!filtered.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                for (TextPosition position : filtered) {
                    builder.append(position.getUnicode());
                }
                String tokenText = builder.toString();
                if (!tokenText.isBlank()) {
                    float tokenX = filtered.stream()
                            .map(TextPosition::getXDirAdj)
                            .min(Float::compareTo)
                            .orElse(0f);
                    float tokenWidth = filtered.stream()
                            .map(TextPosition::getWidthDirAdj)
                            .reduce(0f, Float::sum);
                    if (tokenWidth <= 0f) {
                        tokenWidth = filtered.get(filtered.size() - 1).getWidthDirAdj();
                    }
                    tokenWidth = Math.max(tokenWidth, 0.5f);
                    float tokenY = filtered.stream()
                            .map(TextPosition::getYDirAdj)
                            .min(Float::compareTo)
                            .orElse(0f);
                    resolveLine(tokenY).addToken(new PositionedToken(tokenX, tokenX + tokenWidth, tokenText));
                }
            }
            super.writeString(text, textPositions);
        }

        private TableLine resolveLine(float y) {
            for (TableLine line : lines) {
                if (Math.abs(line.y() - y) < Y_TOLERANCE) {
                    return line;
                }
            }
            TableLine line = new TableLine(y);
            lines.add(line);
            return line;
        }
    }

    /**
     * Computes the rectangle that contains the statement table for a given page.
     * When the header cannot be detected the method falls back to default coordinates.
     *
     * @param pageIndex zero-based page index
     * @param page      PDF page to inspect
     * @param headerY   optional Y-coordinate reported by {@link HeaderDetectingStripper}
     * @return rectangle describing the area to parse or {@code null} when nothing can be extracted
     */
    private Rectangle2D resolveTableRegionForPage(int pageIndex, PDPage page, Float headerY) {
        float pageHeight = page.getMediaBox().getHeight();
        float pageWidth = page.getMediaBox().getWidth();
        float margin = 32f;
        float footerPadding = 40f;
        float startY;
        if (headerY != null) {
            startY = Math.max(margin, headerY - 5f);
        } else {
            startY = pageIndex == 0 ? 160f : 130f;
            log.warn("Table header not found on page {}. Falling back to default region.", pageIndex + 1);
        }
        float availableHeight = Math.max(0, pageHeight - startY - footerPadding);
        float height = availableHeight <= 0 ? pageHeight - (2 * margin) : availableHeight;
        if (height <= 0) {
            return null;
        }
        float width = Math.max(0, pageWidth - (2 * margin));
        return new Rectangle2D.Float(margin, startY, width, height);
    }

    /**
     * Runs a special stripper that captures the Y coordinate for the header line.
     *
     * @param document loaded PDF document
     * @param pageIndex zero-based page index
     * @return Y coordinate of the header or {@code null} when none could be located
     * @throws IOException when PDFBox fails to process the page
     */
    private Float detectTableHeaderY(PDDocument document, int pageIndex) throws IOException {
        HeaderDetectingStripper stripper = new HeaderDetectingStripper();
        configureStripper(stripper);
        stripper.setLineSeparator("\n");
        stripper.setWordSeparator(" ");
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);
        return stripper.getHeaderY();
    }

    /**
     * Performs a lightweight PDF detection check based on MIME type and file name.
     *
     * @param file uploaded file
     * @return {@code true} when the content type or suffix indicates a PDF
     */
    private boolean looksLikePdf(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        String fileName = file.getOriginalFilename();
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * Determines a safe file name that can be shown to the user and stored alongside the result.
     *
     * @param file uploaded file
     * @return original filename or a default placeholder
     */
    private String resolveFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            return "uploaded.pdf";
        }
        return fileName;
    }

    /**
     * Detects whether the provided text block contains the expected table header markers.
     *
     * @param text flattened line value
     * @return {@code true} when the text contains the key header tokens
     */
    private static boolean isHeaderText(String text) {
        if (text == null) {
            return false;
        }
        String flattened = text.replaceAll("[\\s　]", "");
        int typeCount = flattened.split("種別", -1).length - 1;
        int stationCount = flattened.split("利用駅", -1).length - 1;
        boolean hasAmount = flattened.contains("入金") || flattened.contains("利用額") || flattened.contains("利用金額");
        return flattened.contains("月")
                && flattened.contains("日")
                && typeCount >= 2
                && stationCount >= 2
                && hasAmount;
    }

    /**
     * Helper stripper that records the Y position of the table header text while streaming through PDF content.
     */
    private static class HeaderDetectingStripper extends PDFTextStripper {
        private Float headerY;

        /**
         * Creates the stripper and configures the parent PDFTextStripper.
         *
         * @throws IOException propagated from {@link PDFTextStripper}
         */
        HeaderDetectingStripper() throws IOException {
            super();
        }

        /**
         * Captures text segments and remembers the earliest Y coordinate that matches header criteria.
         *
         * @param text          chunk provided by PDFBox
         * @param textPositions glyph positions for the chunk
         * @throws IOException when the superclass fails to write
         */
        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (headerY == null && text != null && !textPositions.isEmpty()) {
                float y = textPositions.stream()
                        .map(TextPosition::getYDirAdj)
                        .min(Float::compareTo)
                        .orElse(textPositions.get(0).getYDirAdj());
                StringBuilder builder = lineCache.computeIfAbsent(Math.round(y), key -> new StringBuilder());
                builder.append(text);
                if (isHeaderText(builder.toString())) {
                    headerY = y;
                }
            }
            super.writeString(text, textPositions);
        }

        /**
         * @return Y coordinate for the header text or {@code null} when undetected
         */
        Float getHeaderY() {
            return headerY;
        }

        private final java.util.Map<Integer, StringBuilder> lineCache = new java.util.HashMap<>();
    }
}
