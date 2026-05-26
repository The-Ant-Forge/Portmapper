# Developer Guide

## Prerequisites

- **Java 21 LTS** (Adoptium Temurin recommended).

## Building

```sh
./gradlew build
java -jar build/libs/portmapper-<version>-all.jar
```

A single command builds and launches:

```sh
./gradlew run
```

## Single-test execution

```sh
./gradlew test --tests org.chris.portmapper.router.sbbi.TestPortMappingExtractor
./gradlew test --tests '*TestPortMappingExtractor.<methodName>'
```

## Apply license headers to new files

```sh
./gradlew licenseFormat
```

Required before committing new source files — the `license` plugin (configured against `gradle/license-header.txt`) will fail `./gradlew build` if any file is missing the header.

## Check for outdated dependencies

```sh
./gradlew dependencyUpdates
```

Useful as we work through the modernization roadmap (Cling → jUPnP, BSAF replacement, etc.).
