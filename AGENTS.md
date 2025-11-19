# Repository Guidelines

## Project Structure & Module Organization
Application logic belongs under `src/main/java`, grouped by feature (`controller`, `service`, `pdf`, etc.) so Spring stereotypes stay isolated. Reusable Thymeleaf views live in `src/main/resources/templates`, while static assets (images, CSS, JS) sit in `src/main/resources/static`. Shared configuration and message bundles go inside `src/main/resources`. Tests mirror the main tree inside `src/test/java`, keeping packages identical so utilities can be imported with minimal wiring. Generated artifacts are written to `build/`, and raw reference documents currently live in `source/`; never modify files in `build/` or `source/` manually.

## Build, Test, and Development Commands
`./gradlew bootRun` starts the Spring Boot app with hot reload-friendly classpath defaults.
`./gradlew clean build` wipes previous outputs and produces a production-ready JAR under `build/libs`.
`./gradlew test` runs the JUnit 5 suite (same task that CI calls).

## Coding Style & Naming Conventions
Target Java 21 and 4-space indentation. Use `UpperCamelCase` for classes/components, `lowerCamelCase` for methods and variables, and align DTO/record names with their resource (e.g., `ApplicationFormDto`). Controllers should end with `Controller`, services with `Service`, and Spring beans must stay package-private unless externalized. Prefer constructor injection, Lombok annotations (`@Getter`, `@Builder`) for pure data holders, and validation annotations from `jakarta.validation` over manual checks. Keep template fragments in `templates/fragments` and name them with the `fragment-*` prefix for clarity.

## Testing Guidelines
Rely on Spring Boot Starter Test + JUnit Platform (`useJUnitPlatform()` is already configured). Name files with the `*Tests` suffix (e.g., `PdfParserTests`) and mirror the package of the class under test. Use `@SpringBootTest` only for integration paths; default to `@WebMvcTest` or plain JUnit for units. New parser or validator logic must include at least one happy-path and one failure-case test, and contributors should verify coverage locally with `./gradlew test --info` when diagnosing flaky behavior.

## Commit & Pull Request Guidelines
This workspace snapshot lacks `.git` history, so align new commits to an imperative Conventional Commit style: `feat(parser): add PDF checksum validation`. Limit subject lines to ~65 characters and include a short body describing validation steps (`./gradlew test`). Pull requests should link the relevant issue, summarize architectural changes, list new commands/config flags, and attach UI screenshots whenever templates change. Refuse PRs without passing CI artifacts or missing test notes.

## Security & Configuration Tips
Never commit real credentials inside `src/main/resources/application.yaml`; rely on environment variables or `.env` entries wired through Spring’s config tree. PDF samples under `source/` may contain sensitive data—copy them before editing and keep derivatives in `src/test/resources`. Always validate user-supplied file names and MIME types before invoking PDFBox to prevent parser exploits.
