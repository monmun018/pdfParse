package com.example.demo.application.service;

import com.example.demo.domain.exception.PdfFileRequiredException;
import com.example.demo.domain.exception.UnsupportedPdfFormatException;
import com.example.demo.domain.model.PdfExtractionResult;
import com.example.demo.domain.model.PdfFeature;
import com.example.demo.domain.model.SuicaPdfType;
import com.example.demo.infrastructure.pdf.PdfBoxMetadataReader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests covering the PDF extraction application service.
 */
class PdfTextServiceTest {

    private final PdfTextService pdfTextService = new PdfTextService(new PdfBoxMetadataReader());

    /**
     * Verifies that PDF content can be parsed and returned.
     *
     * @throws Exception when the sample PDF cannot be created
     */
    @Test
    void extractTextReturnsContent() throws Exception {
        byte[] pdfBytes = createPdf("Hello Spring!");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                pdfBytes
        );

        PdfExtractionResult result = pdfTextService.extractText(file);

        assertThat(result.fileName()).isEqualTo("sample.pdf");
        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.metadata()).isNotNull();
        assertThat(result.rows()).isEmpty();
        assertThat(result.extractedText()).contains("Hello Spring!");
    }

    /**
     * Ensures non-PDF uploads are rejected.
     */
    @Test
    void extractTextRejectsNonPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "plain text".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(UnsupportedPdfFormatException.class, () -> pdfTextService.extractText(file));
    }

    /**
     * Ensures empty uploads are rejected.
     */
    @Test
    void extractTextRequiresFile() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        assertThrows(PdfFileRequiredException.class, () -> pdfTextService.extractText(file));
    }

    /**
     * Provides a smoke test that exercises the parser against a sample PDF under /source.
     *
     * @throws Exception when file IO fails
     */
    @Test
    void extractTextDetectsHeaderInSamplePdf() throws Exception {
        Path sourceDir = Path.of("source");
        assumeTrue(Files.isDirectory(sourceDir), "source directory missing");

        try (Stream<Path> files = Files.list(sourceDir)) {
            Optional<Path> pdfPath = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .findFirst();
            assumeTrue(pdfPath.isPresent(), "No sample PDF available under /source");

            PdfExtractionResult result = pdfTextService.extractText(pdfPath.get());
            assertThat(result.rows()).isNotEmpty();
            assertThat(result.metadata()).isNotNull();
        }
    }

    /**
     * Ensures feature selection gates metadata/text extraction.
     *
     * @throws Exception when the sample PDF cannot be created
     */
    @Test
    void extractTextAllowsSelectingFeatures() throws Exception {
        byte[] pdfBytes = createPdf("Feature gating");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "feature.pdf",
                "application/pdf",
                pdfBytes
        );

        PdfExtractionResult result = pdfTextService.extractText(file, EnumSet.of(PdfFeature.TABLE_ROWS));

        assertThat(result.metadata()).isNull();
        assertThat(result.extractedText()).isNull();
        assertThat(result.documentMetadata()).isNull();
    }

    @Test
    void extractTextSupportsPartialSelectionPdfsWithoutBalance() throws Exception {
        Path partialPdf = Path.of("source/JE80FA22030423963_20251117_20251120221126.pdf");
        assumeTrue(Files.exists(partialPdf), "Partial selection PDF fixture missing");

        PdfExtractionResult result = pdfTextService.extractText(partialPdf, EnumSet.of(
                PdfFeature.TABLE_ROWS,
                PdfFeature.STATEMENT_METADATA,
                PdfFeature.RAW_TEXT
        ));

        assertThat(result.pdfType()).isEqualTo(SuicaPdfType.PARTIAL_SELECTION);
        assertThat(result.rows()).isNotEmpty();
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.balance()).isNullOrEmpty());
        assertThat(result.rows().get(0).amount()).isNotBlank();
    }

    /**
     * Creates an in-memory PDF with the provided text for use in tests.
     *
     * @param text text to render in the PDF
     * @return PDF bytes
     * @throws IOException when PDFBox cannot create or save the document
     */
    private byte[] createPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                contentStream.newLineAtOffset(72, 700);
                contentStream.showText(text);
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
