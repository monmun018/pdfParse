# API Overview

HTTP surface area for Suica PDF Viewer.

## Table of Contents
1. [Base Information](#base-information)
2. [Authentication](#authentication)
3. [Resources](#resources)
   - [GET /](#get-)
   - [POST /extract](#post-extract)
   - [POST /api/extract](#post-apiextract)
   - [POST /export](#post-export)
4. [Errors](#errors)
5. [Rate Limiting](#rate-limiting)
6. [Schema](#schema)

## Base Information
- **Default base URL**: `http://localhost:8080`
- **Versioning**: Not versioned yet; endpoints live at the root path. Introduce `/api/v1/...` when breaking API changes are needed.
- **Media types**:
  - Browser endpoints respond with `text/html` (Thymeleaf templates)
  - `/api/extract` responds with `application/json`
  - `/export` responds with `text/plain` (CSV)

## Authentication
No authentication is implemented. Lock down the service via network controls, reverse proxy auth, or Spring Security before exposing it publicly.

## Resources

### GET /
Renders the upload dashboard.
- **Response**: HTML page (`upload.html`) containing feature toggles and form elements.
- **Caching**: Uses server-side `HttpSession` to pre-populate the last parsed result.

### POST /extract
Processes uploads from the web form and redisplays the page with parsed output.
- **Request**: `multipart/form-data`
  - `file` (required): PDF statement.
  - `features` (optional, repeatable): Accepts any of the enum names below. Defaults to all features when omitted.
    | Enum | Display name |
    | --- | --- |
    | `STATEMENT_METADATA` | Statement metadata |
    | `TABLE_ROWS` | Parsed Suica table |
    | `RAW_TEXT` | Raw PDF text |
    | `DOCUMENT_METADATA` | PDF info dictionary + XMP |
- **Response**: HTML view with success or inline error message.

Example form submission via `curl`:
```bash
curl -X POST http://localhost:8080/extract \
     -F "file=@source/JE80FA22030423963_20251021_20251115001135.pdf" \
     -F "features=TABLE_ROWS" \
     -F "features=DOCUMENT_METADATA"
```

### POST /api/extract
Machine-friendly endpoint that returns parsed data as JSON.
- **Request**: `multipart/form-data` (same fields as `/extract`).
- **Response**: `200 OK` with `PdfExtractionResult` payload or an error body.

Sample success response:
```json
{
  "fileName": "JE80FA22030423963_20251021_20251115001135.pdf",
  "pageCount": 2,
  "metadata": {
    "heading": "モバイルSuica 残高ご利用明細",
    "cardNumberLine": "JE**********1234",
    "historySummary": "残高履歴 (2025/10/21)",
    "createdLine": "ご利用ありがとうございます 2025/11/15",
    "createdDate": "2025-11-15"
  },
  "rows": [
    {
      "rowNumber": 1,
      "yearMonth": "2025-10",
      "month": "10",
      "day": "21",
      "typeIn": "入",
      "stationIn": "登戸",
      "typeOut": "出",
      "stationOut": "新宿",
      "balance": "¥1,359",
      "amount": "¥1,000"
    }
  ],
  "extractedText": "...",
  "documentMetadata": {
    "infoDictionary": {
      "title": "Statement",
      "author": null,
      "subject": null,
      "keywords": null,
      "creator": "JR East",
      "producer": "PDF Library",
      "creationDate": "2025-11-15 00:11:35 JST",
      "modificationDate": null,
      "trapped": null
    },
    "xmpMetadata": {
      "dublinCoreTitle": "Suica Statement",
      "dublinCoreCreators": "JR East",
      "dublinCoreDates": "2025-11-15",
      "createDate": "2025-11-15 00:11:34 JST",
      "creatorTool": "JR System",
      "metadataDate": "2025-11-15 00:11:35 JST"
    },
    "pageCount": 2,
    "pdfVersion": "1.7",
    "encrypted": false,
    "fileSizeBytes": 123456
  }
}
```

Sample validation error (`400`):
```json
{
  "timestamp": "2025-11-17T14:35:12Z",
  "status": 400,
  "error": "DOMAIN_ERROR",
  "message": "Please choose a PDF file to upload.",
  "path": "/api/extract",
  "details": null
}
```

Parsing failure (`500`):
```json
{
  "timestamp": "2025-11-17T14:35:44Z",
  "status": 500,
  "error": "INFRASTRUCTURE_ERROR",
  "message": "Unable to process the uploaded PDF file.",
  "path": "/api/extract",
  "details": null
}
```

### POST /export
Streams the user-selected rows as CSV.
- **Request**: `application/x-www-form-urlencoded`
  - `rowIds` (required, repeatable): Row numbers (as produced in the prior extraction) to include.
- **Behavior**: Uses the last `PdfExtractionResult` stored in the session. If no cached result exists or no rows are selected, the endpoint returns `400` with a plain-text explanation.
- **Response**: `200 OK` with `Content-Disposition: attachment; filename="suica-export.csv"` and comma-separated rows.

Example `curl` call after a browser session parsed data (cookie jar reused):
```bash
curl -X POST http://localhost:8080/export \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -H "Cookie: JSESSIONID=<from previous response>" \
     --data-urlencode "rowIds=1" \
     --data-urlencode "rowIds=2" \
     -OJ
```

## Errors
| Status | When | Body |
| --- | --- | --- |
| `400 Bad Request` | Domain validation failures (missing file, unsupported type, invalid path) | `ErrorResponse` with `error=DOMAIN_ERROR` |
| `404 Not Found` | PDF path supplied to `/api/extract` does not exist (server-side usage). | `ErrorResponse` with `error=PDF_NOT_FOUND` |
| `422 Unprocessable Entity` | Application validation failures (`CsvExportService`, custom use cases). | `ErrorResponse` with `error=CSV_EXPORT_VALIDATION_ERROR` or `error=APPLICATION_ERROR` |
| `500 Internal Server Error` | Infrastructure failures (PDFBox, IO) or unexpected runtime errors. | `ErrorResponse` with `error=INFRASTRUCTURE_ERROR`/`UNEXPECTED_ERROR` |

Server-side validation exceptions that occur on the HTML endpoints are rendered inline inside the Thymeleaf page, while API consumers always receive the JSON `ErrorResponse` format shown above.

## Rate Limiting
No rate limiting or throttling is implemented. Add it at the reverse proxy or via Spring filters if this service will be exposed publicly.

## Schema
`PdfExtractionResult` structure (JSON field names provided by Java record accessors):
- `fileName` (`string`)
- `pageCount` (`integer`)
- `metadata` (`StatementMetadata` object or `null`)
  - `heading`, `cardNumberLine`, `historySummary`, `createdLine` (`string|null`)
  - `createdDate` (`ISO-8601 date` or `null`)
- `rows` (array of `SuicaStatementRow`)
  - `rowNumber` (`integer`)
  - `yearMonth`, `month`, `day`, `typeIn`, `stationIn`, `typeOut`, `stationOut`, `balance`, `amount` (`string`)
- `extractedText` (`string|null`)
- `documentMetadata` (`PdfDocumentMetadata` object or `null`)
  - `infoDictionary` (`PdfInfoDictionary` or `null`)
  - `xmpMetadata` (`PdfXmpMetadata` or `null`)
  - `pageCount` (`integer`)
  - `pdfVersion` (`string`)
  - `encrypted` (`boolean`)
  - `fileSizeBytes` (`long`)
