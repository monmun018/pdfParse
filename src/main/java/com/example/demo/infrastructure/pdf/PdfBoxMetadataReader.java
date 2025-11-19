package com.example.demo.infrastructure.pdf;

import com.example.demo.domain.model.PdfDocumentMetadata;
import com.example.demo.domain.model.PdfInfoDictionary;
import com.example.demo.domain.model.PdfXmpMetadata;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.DomXmpParser;
import org.apache.xmpbox.xml.XmpParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Infrastructure service that turns PDFBox metadata into structured DTOs consumed by the domain.
 * Hides the PDFBox parsing details from the rest of the application.
 */
@Service
public class PdfBoxMetadataReader {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxMetadataReader.class);
    private static final DateTimeFormatter CALENDAR_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    /**
     * Reads the metadata from a {@link PDDocument} and maps it into our domain DTOs.
     *
     * @param document      already opened PDF document
     * @param fileSizeBytes original file size reported by the upload
     * @return structured metadata or {@code null} when the source is missing
     */
    public PdfDocumentMetadata readMetadata(PDDocument document, long fileSizeBytes) {
        if (document == null) {
            return null;
        }
        PdfInfoDictionary info = extractInfo(document.getDocumentInformation());
        PdfXmpMetadata xmp = extractXmp(document.getDocumentCatalog());

        return new PdfDocumentMetadata(
                info,
                xmp,
                document.getNumberOfPages(),
                String.valueOf(document.getDocument().getVersion()),
                document.isEncrypted(),
                fileSizeBytes
        );
    }

    /**
     * Extracts the legacy info dictionary fields from the PDF document.
     *
     * @param info info dictionary from PDFBox
     * @return mapped domain DTO or {@code null}
     */
    private PdfInfoDictionary extractInfo(PDDocumentInformation info) {
        if (info == null) {
            return null;
        }
        return new PdfInfoDictionary(
                info.getTitle(),
                info.getAuthor(),
                info.getSubject(),
                info.getKeywords(),
                info.getCreator(),
                info.getProducer(),
                formatCalendar(info.getCreationDate()),
                formatCalendar(info.getModificationDate()),
                info.getTrapped()
        );
    }

    /**
     * Extracts the XMP metadata payload and converts it into the {@link PdfXmpMetadata} DTO.
     *
     * @param catalog document catalog pointer supplied by PDFBox
     * @return parsed XMP metadata or {@code null} if missing/invalid
     */
    private PdfXmpMetadata extractXmp(PDDocumentCatalog catalog) {
        if (catalog == null) {
            return null;
        }
        PDMetadata pdMetadata = catalog.getMetadata();
        if (pdMetadata == null) {
            return null;
        }
        try (InputStream metadataStream = pdMetadata.exportXMPMetadata()) {
            if (metadataStream == null) {
                return null;
            }
            DomXmpParser parser = new DomXmpParser();
            parser.setStrictParsing(false);
            XMPMetadata xmp = parser.parse(metadataStream);
            DublinCoreSchema dc = xmp.getDublinCoreSchema();
            XMPBasicSchema basic = xmp.getXMPBasicSchema();

            List<String> creators = dc != null && dc.getCreators() != null
                    ? List.copyOf(dc.getCreators())
                    : List.of();
            List<String> dates = dc != null && dc.getDates() != null
                    ? dc.getDates().stream().filter(Objects::nonNull).map(Object::toString).toList()
                    : List.of();
            String dcTitle = dc != null && dc.getTitle() != null ? dc.getTitle().toString() : null;

            return new PdfXmpMetadata(
                    dcTitle,
                    joinValues(creators),
                    joinValues(dates),
                    basic != null ? formatCalendar(basic.getCreateDate()) : null,
                    basic != null ? basic.getCreatorTool() : null,
                    basic != null ? formatCalendar(basic.getMetadataDate()) : null
            );
        } catch (IOException | XmpParsingException ex) {
            log.warn("Failed to parse XMP metadata", ex);
            return null;
        }
    }

    /**
     * Formats a {@link Calendar} value into a stable ISO-like representation for the UI.
     *
     * @param calendar calendar coming from PDFBox metadata
     * @return formatted string or {@code null}
     */
    private String formatCalendar(Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        return Optional.of(calendar.toInstant())
                .map(instant -> instant.atZone(ZoneId.systemDefault()))
                .map(CALENDAR_FORMATTER::format)
                .orElse(null);
    }

    /**
     * Joins list values with a comma separator while handling empty/null collections.
     *
     * @param values metadata list to join
     * @return comma separated string or {@code null}
     */
    private String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(", ", values);
    }
}
