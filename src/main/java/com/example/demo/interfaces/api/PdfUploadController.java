package com.example.demo.interfaces.api;

import com.example.demo.application.exception.ApplicationException;
import com.example.demo.application.service.CsvExportService;
import com.example.demo.application.service.PdfTextService;
import com.example.demo.domain.exception.DomainException;
import com.example.demo.domain.model.PdfExtractionResult;
import com.example.demo.domain.model.PdfFeature;
import com.example.demo.infrastructure.exception.InfrastructureException;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;

/**
 * Interfaces-layer MVC controller that handles Suica PDF uploads and CSV exports.
 */
@Controller
public class PdfUploadController {

    private static final String SESSION_RESULT_KEY = "LATEST_PDF_RESULT";
    private static final String SESSION_FEATURES_KEY = "LATEST_PDF_FEATURES";

    private final PdfTextService pdfTextService;
    private final CsvExportService csvExportService;

    /**
     * Creates the controller with the required application services.
     *
     * @param pdfTextService service responsible for parsing PDFs
     * @param csvExportService service responsible for CSV generation
     */
    public PdfUploadController(PdfTextService pdfTextService, CsvExportService csvExportService) {
        this.pdfTextService = pdfTextService;
        this.csvExportService = csvExportService;
    }

    /**
     * Renders the upload page and pre-populates it with any cached result from the session.
     *
     * @param model   model used to expose attributes to the Thymeleaf view
     * @param session HTTP session storing the last extraction result
     * @return upload view name
     */
    @GetMapping("/")
    public String showUploadForm(Model model, HttpSession session) {
        PdfExtractionResult sessionResult = (PdfExtractionResult) session.getAttribute(SESSION_RESULT_KEY);
        model.addAttribute("result", sessionResult);
        model.addAttribute("error", null);
        model.addAttribute("availableFeatures", PdfFeature.values());
        model.addAttribute("selectedFeatures", resolveSessionFeatures(session));

        return "upload";
    }

    /**
     * Handles form submissions for PDF extraction.
     *
     * @param file           uploaded PDF
     * @param featureParams  selected feature list from the checkbox group (optional)
     * @param model          model used for view rendering
     * @param session        HTTP session for caching the result
     * @return upload view name populated with success or error data
     */
    @PostMapping("/extract")
    public String handleUpload(@RequestParam("file") MultipartFile file,
                               @RequestParam(value = "features", required = false) List<String> featureParams,
                               Model model,
                               HttpSession session) {
        EnumSet<PdfFeature> features = PdfFeature.fromStrings(featureParams);
        model.addAttribute("availableFeatures", PdfFeature.values());
        model.addAttribute("selectedFeatures", features);

        try {
            PdfExtractionResult result = pdfTextService.extractText(file, features);
            session.setAttribute(SESSION_RESULT_KEY, result);
            session.setAttribute(SESSION_FEATURES_KEY, features);
            model.addAttribute("result", result);
            model.addAttribute("error", null);
        } catch (DomainException | ApplicationException ex) {
            model.addAttribute("result", null);
            model.addAttribute("error", ex.getMessage());
        } catch (InfrastructureException ex) {
            model.addAttribute("result", null);
            model.addAttribute("error", "We couldn't read that PDF. Please try another file.");
        }

        return "upload";
    }

    /**
     * REST endpoint that mirrors the HTML upload form but returns JSON.
     *
     * @param file          uploaded PDF
     * @param featureParams requested feature list (optional)
     * @return JSON response containing the extraction result
     */
    @PostMapping(value = "/api/extract", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<PdfExtractionResult> handleUploadApi(@RequestParam("file") MultipartFile file,
                                                               @RequestParam(value = "features", required = false) List<String> featureParams) {
        EnumSet<PdfFeature> features = PdfFeature.fromStrings(featureParams);
        PdfExtractionResult result = pdfTextService.extractText(file, features);
        return ResponseEntity.ok(result);
    }

    /**
     * Streams the selected rows as a CSV download.
     *
     * @param rowIds  selected row identifiers provided by the UI
     * @param session HTTP session storing the cached extraction result
     * @return CSV document as a {@link ResponseEntity}
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(name = "rowIds", required = false) List<Integer> rowIds,
                                            HttpSession session) {
        PdfExtractionResult cached = (PdfExtractionResult) session.getAttribute(SESSION_RESULT_KEY);
        String csv = csvExportService.exportSelectedRows(cached, rowIds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"suica-export.csv\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Resolves and sanitizes the cached feature selection stored in the session.
     *
     * @param session HTTP session
     * @return feature set ready to pre-populate checkboxes
     */
    @SuppressWarnings("unchecked")
    private EnumSet<PdfFeature> resolveSessionFeatures(HttpSession session) {
        Object cached = session.getAttribute(SESSION_FEATURES_KEY);
        if (cached instanceof EnumSet<?> enumSet && !enumSet.isEmpty()) {
            EnumSet<PdfFeature> copy = EnumSet.noneOf(PdfFeature.class);
            enumSet.forEach(value -> {
                if (value instanceof PdfFeature feature) {
                    copy.add(feature);
                }
            });
            if (!copy.isEmpty()) {
                return copy;
            }
        }
        if (cached instanceof List<?> list && !list.isEmpty()) {
            EnumSet<PdfFeature> features = EnumSet.noneOf(PdfFeature.class);
            for (Object value : list) {
                if (value instanceof PdfFeature feature) {
                    features.add(feature);
                }
            }
            if (!features.isEmpty()) {
                return features;
            }
        }
        return PdfFeature.allFeatures();
    }

}
