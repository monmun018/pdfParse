# Setup & Deployment

How to install, configure, build, and ship the Suica PDF Viewer service.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Environment Configuration](#environment-configuration)
3. [Local Setup](#local-setup)
4. [Build Process](#build-process)
5. [Deployment](#deployment)
6. [Common Issues & Troubleshooting](#common-issues--troubleshooting)
7. [Related Docs](#related-docs)

## Prerequisites
- **Java Development Kit 21** (the Gradle toolchain targets Java 21; older JDKs will fail with `Unsupported class file major version`).
- **Gradle Wrapper** (already included as `./gradlew`). No global Gradle installation is required.
- **Git** (optional, for source control workflows).
- **PDF samples** (optional). A Suica statement example lives under `source/`; copy it before editing because the directory should remain read-only.

## Environment Configuration
The project does not define custom environment variables yet. Standard Spring Boot properties apply:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SPRING_APPLICATION_NAME` | Overrides `spring.application.name` if needed. | `demo` (set in `application.yaml`). |
| `SPRING_PROFILES_ACTIVE` | Activate profile-specific config. | *(unset)* |
| `SERVER_PORT` / `server.port` | Run HTTP server on a custom port. | `8080` |
| `SPRING_SERVLET_MULTIPART_MAX-FILE-SIZE` | Increase upload size limit if larger statements fail. | Boot defaults |

> **Note:** Keep credentials out of `src/main/resources/application.yaml`. Inject secrets via env vars or externalized config if future features require them.

## Local Setup
1. **Clone / download** the repository into your workspace (e.g., `/Users/<you>/workspace/demo`).
2. **Verify Java**: `java -version` should print a 21.x build.
3. **Prime Gradle**: `./gradlew --version` (downloads the wrapper distribution if missing).
4. **Run the application**:
   ```bash
   ./gradlew bootRun
   ```
5. Navigate to `http://localhost:8080/`, upload a Suica PDF (see `source/` for a sample), select features, and submit. The UI will render metadata, rows, and optional raw text.
6. Keep the terminal open; Gradle's dev server will hot reload classpath changes.

## Build Process
- **Clean + compile + test + package**:
  ```bash
  ./gradlew clean build
  ```
  This creates `build/libs/demo-0.0.1-SNAPSHOT.jar` and runs all `test` tasks.
- **Run tests only**: `./gradlew test`
- **Bootable JAR execution**:
  ```bash
  java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
  ```

## Deployment
Because the service is stateless, deployment is straightforward:
1. Build the jar (`./gradlew clean build`).
2. Copy `build/libs/demo-0.0.1-SNAPSHOT.jar` to the target environment.
3. Configure any desired Spring Boot environment variables (e.g., `SERVER_PORT`, logging config, multipart limits).
4. Launch with `java -jar` under a process manager (systemd, Supervisor, container entrypoint, etc.).
5. Expose port 8080 (or your override) behind a reverse proxy or load balancer if operating in production.

For containerized deployments, wrap the jar in your preferred base image (e.g., `eclipse-temurin:21-jre`) and map `8080/tcp`. No database or message broker is needed.

## Common Issues & Troubleshooting
| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `Unsupported class file major version 65` | Running Gradle with JDK < 21. | Install Java 21 (Temurin, Zulu, etc.) and update `JAVA_HOME`.
| `Only PDF uploads are supported.` error | Uploaded file extension/`Content-Type` was not PDF. | Ensure `multipart/form-data` request names the file field `file` and includes a `.pdf`.
| `We couldn't read that PDF...` message | PDFBox failed to parse (corrupt/encrypted PDF). | Try exporting an unencrypted statement, or extend `PdfTextService` with encryption handling.
| `/export` responds with `Please select at least one row...` | User attempted export before parsing or without selection. | Upload a PDF first, then check at least one row checkbox.
| Sample test skipped (`source directory missing`) | `PdfTextServiceTest` uses files in `source/`. | Ensure the repo contains sample PDFs or adjust the test.

## Related Docs
- [Project README](README.md)
- [Architecture](architecture.md)
- [API Overview](api-overview.md)
- [Contributing](contributing.md)
