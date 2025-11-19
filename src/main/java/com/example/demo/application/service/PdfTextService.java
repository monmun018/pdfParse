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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final Pattern CURRENCY_TOKEN = Pattern.compile("^[+-]?[\\\\¥]?[\\d,]+$");
    private static final Set<String> ENTRY_ONLY_TYPES = Set.of("ｶｰﾄﾞ", "カード", "ﾓﾊﾞｲﾙ", "モバイル", "物販");

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

            boolean needsDocumentText = features.contains(PdfFeature.RAW_TEXT) || features.contains(PdfFeature.STATEMENT_METADATA);
            if (needsDocumentText) {
                rawText = extractFullDocumentText(document);
            }
            if (features.contains(PdfFeature.STATEMENT_METADATA)) {
                metadata = extractMetadata(rawText);
            }
            if (features.contains(PdfFeature.TABLE_ROWS)) {
                String tableText = extractStatementTable(document);
                rows = parseTableText(tableText, metadata);
            }

            return new PdfExtractionResult(
                    fileName,
                    document.getNumberOfPages(),
                    metadata,
                    rows,
                    features.contains(PdfFeature.RAW_TEXT) ? rawText : null,
                    features.contains(PdfFeature.DOCUMENT_METADATA)
                            ? metadataReader.readMetadata(document, bytes.length)
                            : null
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
    private String extractStatementTable(PDDocument document) throws IOException {
        StringBuilder builder = new StringBuilder();

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            PDPage page = document.getPage(pageIndex);
            Float headerY = detectTableHeaderY(document, pageIndex);
            Rectangle2D region = resolveTableRegionForPage(pageIndex, page, headerY);
            if (region == null) {
                continue;
            }

            PDFTextStripperByArea areaStripper = new PDFTextStripperByArea();
            configureStripper(areaStripper);
            areaStripper.setLineSeparator("\n");
            areaStripper.setWordSeparator(" ");
            areaStripper.setParagraphEnd("\n");
            areaStripper.setAddMoreFormatting(true);
            areaStripper.setAverageCharTolerance(0.12f);
            areaStripper.setSpacingTolerance(0.2f);
            areaStripper.addRegion("table", region);
            areaStripper.extractRegions(page);
            String tablePageText = areaStripper.getTextForRegion("table").strip();
            if (!tablePageText.isEmpty()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append(tablePageText);
            }
        }
        return builder.toString().strip();
    }

    /**
     * Converts the extracted table text into domain rows.
     *
     * @param tableText raw multiline table content
     * @param metadata  statement metadata used for contextual fields
     * @return parsed row list (possibly empty)
     */
    private List<SuicaStatementRow> parseTableText(String tableText, StatementMetadata metadata) {
        if (tableText.isEmpty()) {
            return Collections.emptyList();
        }

        List<SuicaStatementRow> rows = new ArrayList<>();
        int rowNumber = 1;
        boolean headerSeen = false;
        for (String rawLine : tableText.split("\\R")) {
            String line = rawLine.strip();
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
        List<String> tokens = tokenize(line);
        SuicaStatementRow row = toRow(tokens, rowNumber, metadata);
        if (row != null) {
            rows.add(row);
            rowNumber++;
        }
        }
        return rows;
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
     * Tokenizes a table row by trying several separator strategies (tabs, double spaces, fallback singles).
     *
     * @param line original line extracted from the PDF
     * @return ordered tokens ready for mapping to domain fields
     */
    private List<String> tokenize(String line) {
        String normalized = line.replace('\u3000', ' ');
        List<String> tokens = new ArrayList<>(Arrays.stream(normalized.split("\t"))
                .map(this::cleanToken)
                .filter(token -> !token.isEmpty())
                .toList());

        if (tokens.size() < 6) {
            tokens = new ArrayList<>(Arrays.stream(normalized.split("\\s{2,}"))
                    .map(this::cleanToken)
                    .filter(token -> !token.isEmpty())
                    .toList());
        }
        if (tokens.size() < 5) {
            tokens = new ArrayList<>(Arrays.stream(normalized.split("\\s+"))
                    .map(this::cleanToken)
                    .filter(token -> !token.isEmpty())
                    .toList());
        }
        return tokens;
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
     * @param originalTokens tokens extracted from one table line
     * @param rowNumber      sequential row counter used for display
     * @param metadata       heading metadata required to compute the year-month column
     * @return parsed row or {@code null} when the tokens do not look like a data row
     */
    private SuicaStatementRow toRow(List<String> originalTokens, int rowNumber, StatementMetadata metadata) {
        if (originalTokens.size() < 3) {
            return null;
        }

        List<String> tokens = new ArrayList<>(originalTokens);
        String amount = extractCurrency(tokens, false);
        String balance = extractCurrency(tokens, true);

        String month = getToken(tokens, 0);
        String day = getToken(tokens, 1);
        String typeIn = getToken(tokens, 2);
        String stationIn = getToken(tokens, 3);
        String typeOut = "";
        String stationOut = "";

        boolean fallbackMapping = false;
        if (tokens.size() >= 6) {
            typeOut = getToken(tokens, 4);
            stationOut = getToken(tokens, 5);
        } else {
            fallbackMapping = true;
        }

        if (ENTRY_ONLY_TYPES.contains(typeIn)) {
            typeOut = "";
            stationOut = "";
        }

        if (stationIn != null && looksLikeExitType(stationIn) && typeOut.isEmpty()) {
            // stationIn actually belongs to exit columns when inbound station missing.
            typeOut = stationIn;
            stationIn = "";
        }

        if (fallbackMapping && (!balance.isEmpty() || !amount.isEmpty())) {
            log.debug("Row {} parsed with fallback column mapping. tokens={}", rowNumber, originalTokens);
        }

        if (month.isEmpty() && day.isEmpty()) {
            return null;
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

    /**
     * Removes and returns the last currency-looking token from the row.
     *
     * @param tokens    mutable token list (will be modified)
     * @param prefixYen whether to force a leading Yen sign
     * @return normalized currency value or empty string
     */
    private String extractCurrency(List<String> tokens, boolean prefixYen) {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if (isCurrencyToken(token)) {
                tokens.remove(i);
                return normalizeCurrency(token, prefixYen);
            }
        }
        return "";
    }

    /**
     * Determines if a token should be treated as a currency column.
     *
     * @param token token to check
     * @return {@code true} when the token looks like an amount or balance
     */
    private boolean isCurrencyToken(String token) {
        if (token == null) {
            return false;
        }
        String cleaned = token.replace(",", "").replace("¥", "").replace("\\", "");
        return CURRENCY_TOKEN.matcher(token).matches() || cleaned.matches("^[+-]?\\d+$");
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
     * Safely retrieves a token from the list.
     *
     * @param tokens token list
     * @param index  position to read
     * @return value or empty string when out of bounds
     */
    private String getToken(List<String> tokens, int index) {
        if (index >= tokens.size()) {
            return "";
        }
        String token = tokens.get(index);
        return token == null ? "" : token.trim();
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
                && flattened.contains("残高")
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
