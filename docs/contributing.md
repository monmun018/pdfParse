# Contributing Guide

Guidelines for extending the Suica PDF Viewer as a senior engineer.

## Table of Contents
1. [Workflow](#workflow)
2. [Development Environment](#development-environment)
3. [Project Structure Expectations](#project-structure-expectations)
4. [Code Style & Patterns](#code-style--patterns)
5. [Testing Expectations](#testing-expectations)
6. [Git & Commit Hygiene](#git--commit-hygiene)
7. [Submitting Changes](#submitting-changes)
8. [Code Review Checklist](#code-review-checklist)

## Workflow
1. Create a feature branch from `main` (e.g., `feature/parser-tweaks`).
2. Build and run tests locally as you iterate.
3. Keep changes scoped (parser fixes vs. UI tweaks vs. docs) so reviews stay focused.

## Development Environment
- Use **Java 21**; the Gradle toolchain enforces it.
- Run the app with `./gradlew bootRun` for manual verification.
- Reuse the provided sample PDF under `source/` for local testing, but never modify files in `source/` or `build/`—copy them into `src/test/resources` if you need custom fixtures.

## Project Structure Expectations
- Application logic now lives under DDD-aligned roots: `interfaces/api` (controllers + handlers), `application/service` (use case orchestration), `domain/model|exception` (records + rules), and `infrastructure/**` (PDFBox adapters, repositories, HTTP clients). Keep code inside its layer and avoid referencing outward (domain must remain framework-free).
- Views belong in `src/main/resources/templates`; shared fragments should go into `templates/fragments` with the `fragment-*.html` naming convention when you add them.
- Static assets (CSS/JS/images) belong in `src/main/resources/static`.
- Tests mirror the main tree under `src/test/java`, using identical packages so utilities can be imported easily.

## Code Style & Patterns
- Indent with **4 spaces** and target Java 21 language features (records, switch expressions, etc.).
- Naming: classes in `UpperCamelCase`, methods/fields in `lowerCamelCase`. Controllers end with `Controller`, services with `Service`.
- Prefer constructor injection; Spring components should be package-private unless they must be shared.
- Use Lombok (`@Getter`, `@Builder`, etc.) for pure data holders when it improves readability (dependency already declared).
- Favor Jakarta validation annotations over manual guards for incoming request models.
- Follow the existing patterns in `PdfTextService` when expanding parsing logic (e.g., isolate heuristics, log fallbacks, reuse `PdfFeature` toggling instead of ad-hoc flags) and throw the appropriate domain/application exception type instead of generic `RuntimeException`.
- Never commit real credentials to `application.yaml`. Wire secrets through environment variables or profile-specific config.

## Testing Expectations
- Add unit tests for every new parser/validator branch: at least one **happy path** and one **failure path**.
- Keep `@SpringBootTest` usage minimal; default to plain JUnit or `@WebMvcTest` unless full context wiring is required.
- Name test classes with the `*Tests` suffix and mirror the package of the code under test.
- Run `./gradlew test` locally before sending changes. Use `./gradlew test --info` when diagnosing flaky behavior.

## Git & Commit Hygiene
- This repo snapshot lacks `.git` history; when upstreaming, use **Conventional Commit** subjects (e.g., `feat(parser): add XMP checksum validation`). Keep subjects imperative and under ~65 characters.
- Separate unrelated work into distinct commits to make cherry-picks and rollbacks painless.
- Document manual verification in commit bodies (e.g., `- ./gradlew test`).

## Submitting Changes
1. Rebase onto the latest `main` (or target branch) before opening a PR.
2. Ensure `./gradlew clean build` succeeds; attach logs if CI differs.
3. Update relevant docs (`docs/*.md`, README snippets, UI screenshots) whenever you adjust templates, endpoints, workflows, or exception mappings. Template changes should include fresh UI screenshots in the PR description.
4. When adding PDF fixtures, store sanitized copies under `src/test/resources` and reference them from tests.

## Code Review Checklist
- [ ] Input validation covers both empty files and unsupported MIME types (see `PdfUploadController`).
- [ ] Feature toggles default sensibly (`PdfFeature.allFeatures()`) and are propagated to service calls.
- [ ] Session usage is deliberate—clean up or overwrite stale data as needed.
- [ ] CSV export escapes commas/quotes and filters by `rowIds` exactly as expected.
- [ ] Tests fail fast when `source/` assets are missing (use `Assume` as in `PdfTextServiceTest`).
- [ ] No direct modifications to `build/` or `source/` artifacts.
