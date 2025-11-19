package com.example.demo.interfaces.api;

import com.example.demo.application.exception.CsvExportValidationException;
import com.example.demo.application.service.CsvExportService;
import com.example.demo.application.service.PdfTextService;
import com.example.demo.domain.exception.PdfFileRequiredException;
import com.example.demo.infrastructure.exception.PdfProcessingException;
import com.example.demo.interfaces.api.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.anySet;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc tests that validate the controller-to-exception-handler integration.
 */
@WebMvcTest(controllers = PdfUploadController.class)
@Import(GlobalExceptionHandler.class)
class PdfUploadControllerApiTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfTextService pdfTextService;

    @MockBean
    private CsvExportService csvExportService;

    /**
     * Verifies that domain errors translate to HTTP 400 responses.
     *
     * @throws Exception when the mock request fails
     */
    @Test
    void domainExceptionMappedToBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", "data".getBytes());
        BDDMockito.given(pdfTextService.extractText(BDDMockito.any(MultipartFile.class), anySet()))
                .willThrow(new PdfFileRequiredException());

        mockMvc.perform(multipart("/api/extract").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DOMAIN_ERROR"));
    }

    /**
     * Verifies that infrastructure errors translate to HTTP 500 responses.
     *
     * @throws Exception when the mock request fails
     */
    @Test
    void infrastructureExceptionMappedToServerError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", "data".getBytes());
        BDDMockito.given(pdfTextService.extractText(BDDMockito.any(MultipartFile.class), anySet()))
                .willThrow(new PdfProcessingException("Unable", new RuntimeException("boom")));

        mockMvc.perform(multipart("/api/extract").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INFRASTRUCTURE_ERROR"));
    }

    /**
     * Verifies that CSV export validation errors translate to HTTP 422 responses.
     *
     * @throws Exception when the mock request fails
     */
    @Test
    void csvExportValidationExceptionMappedTo422() throws Exception {
        BDDMockito.given(csvExportService.exportSelectedRows(BDDMockito.any(), BDDMockito.any()))
                .willThrow(new CsvExportValidationException("No parsed rows available for export."));

        mockMvc.perform(post("/export")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("CSV_EXPORT_VALIDATION_ERROR"));
    }
}
