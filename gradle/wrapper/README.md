# Gradle Wrapper bootstrap

The source handoff environment had no Gradle and could not resolve external hosts. Consequently no wrapper JAR is fabricated or bundled here.

On first execution, the root `gradlew` script downloads **Gradle 9.6.1** and its SHA-256 from the official HTTPS endpoint, verifies the archive, and generates the official Wrapper in an isolated temporary project. The generated scripts/JAR replace the bootstrap, and the verified distribution checksum is pinned in `gradle-wrapper.properties`. Commit those generated wrapper files after your first successful local build.

The checksum is initially fetched from the same official distribution service, not independently attested. Review the official checksum through your trusted release channel before release builds. No dependency versions are dynamically selected.
