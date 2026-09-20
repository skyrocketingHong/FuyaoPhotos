# Gradle Wrapper

This project includes the official Gradle 9.6.1 Wrapper scripts and JAR, generated with the locally installed Gradle 9.6.1 distribution.

`gradle-wrapper.properties` pins the official distribution SHA-256. `./gradlew` uses an existing matching distribution cache, or downloads and verifies the distribution on first use. No separate bootstrap is required to open the project in Android Studio.

`scripts/bootstrap-gradle.sh` is retained only as a recovery helper for missing Wrapper files. The checksum was retrieved from the official Gradle distribution service; it is not an independent supply-chain attestation.
