# Suica PDF Viewer

A Spring Boot application that extracts JR East Suica statement details from uploaded PDF files and surfaces them via a friendly web UI, JSON API, and CSV export.

## Table of Contents
1. [Overview](#overview)
2. [Main Features](#main-features)
3. [Technology Stack](#technology-stack)
4. [Project Structure](#project-structure)
5. [Quick Start](#quick-start)
6. [Testing](#testing)
7. [Additional Documentation](#additional-documentation)

## Overview
- **Purpose**: Help riders, accountants, and developers inspect Suica e-money usage without relying on JR East's web UI.
- **Target users**: Finance teams reconciling commuter expenses, backend engineers prototyping PDF parsing, and QA engineers validating PDF metadata.
- **Key benefits**:
  - Extracts structured transactions, statement metadata, and raw text from a single upload.
  - Provides a visual review workflow with pagination, feature toggles, and CSV export.
  - Ships with an `/api/extract` endpoint so downstream services can reuse the parser.

## Main Features
- Drag-and-drop style upload form served at `/` with Thymeleaf.
- Configurable parsing features (statement metadata, table rows, raw text, PDF info/XMP metadata) backed by `PdfFeature`.
- Error messaging for invalid file types and unreadable documents.
- Table with checkbox selection + CSV export that respects the user's chosen rows.
- JSON API returning `PdfExtractionResult` payloads for integrations.
- Optional caching of the latest result in `HttpSession` so follow-up actions (export) reuse existing work.
- Global exception handler that translates domain/application/infrastructure errors into a consistent JSON error schema for `/api/**` consumers.

## Technology Stack
| Layer | Notable libraries | Notes |
| --- | --- | --- |
| Runtime | Java 21, Spring Boot 3.5.7 | Bootstraps MVC, validation, multipart upload handling.
| Web/UI | Spring MVC, Thymeleaf | Single controller (`PdfUploadController`) renders `upload.html`.
| PDF parsing | Apache PDFBox 3.0.6, XMPBox 2.0.31 | `PdfTextService` and `PdfBoxMetadataReader` handle text, table-region extraction, and metadata parsing.
| CSV/export | Hand-crafted CSV builder (dependency on `com.opencsv:opencsv:5.12.0` is present but unused today) | Keeps export lightweight.
| Testing | Spring Boot Starter Test (JUnit 5, AssertJ) | Includes multipart and feature-gating tests in `PdfTextServiceTest`.
| Build | Gradle Wrapper | `./gradlew` handles dependency resolution and toolchain (Java 21).

## Project Structure
| Path | Description |
| --- | --- |
| `src/main/java/com/example/demo/DemoApplication.java` | Spring Boot entry point.
| `src/main/java/com/example/demo/interfaces/api` | MVC controller + REST endpoints plus the global API error handler.
| `src/main/java/com/example/demo/application/service` | Application services such as `PdfTextService` (extraction) and `CsvExportService` (CSV building).
| `src/main/java/com/example/demo/domain/model` | Domain records (`PdfExtractionResult`, `PdfFeature`, etc.).
| `src/main/java/com/example/demo/domain/exception` | DDD-aligned domain exceptions (file required, unsupported format, not found).
| `src/main/java/com/example/demo/infrastructure` | PDFBox integration services and infrastructure exceptions.
| `src/main/resources/templates/upload.html` | Thymeleaf view with feature selection, row table, and client-side pagination.
| `src/main/resources/application.yaml` | Minimal Spring configuration (`spring.application.name`).
| `src/test/java/com/example/demo` | Context smoke test + parser-focused unit tests.
| `source/` | Reference Suica statement PDFs (read-only; used in tests and manual verification).
| `build/` | Generated Gradle output (never edit manually).

## Quick Start
1. **Prerequisites**: Java 21 (matching the Gradle toolchain) and the provided Gradle Wrapper. No external services are required.
2. **Install dependencies**: `./gradlew --version` (first run downloads the wrapper distribution/cache).
3. **Run the app**:
   ```bash
   ./gradlew bootRun
   ```
4. Open `http://localhost:8080/`, upload a Suica PDF (there is a sample under `source/`), toggle the desired features, and submit.
5. Use the checkboxes to select parsed rows and click **Export CSV**; application-level validation is enforced via `CsvExportService` and surfaced consistently through the global error handler.

## Testing
- Run the entire test suite: `./gradlew test`
- `PdfTextServiceTest` covers Multipart parsing, invalid file handling, sample PDF parsing (when `source/` is populated), and feature gating.
- `DemoApplicationTests` validates the Spring context wiring via `@SpringBootTest`.
- Add new unit tests under `src/test/java` mirroring the package you touch. Parser or validator updates should introduce both success and failure scenarios per the repository guidelines.

## Additional Documentation
- [Architecture](architecture.md)
- [Setup & Deployment](setup-and-deployment.md)
- [API Overview](api-overview.md)
- [Contributing Guide](contributing.md)
