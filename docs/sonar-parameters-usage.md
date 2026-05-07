# Using Calculated Sonar Parameters

Explains how the parameters produced by `CalculateSonarParameters` are consumed by the different SonarQube analysis methods.

---

## Analysis Methods

### 1. SonarRunner Metarunner

The `SonarRunner` metarunner uses the TeamCity SonarQube Runner plugin. Parameters are passed via the `additionalParameters` field, with **each parameter on its own line**.

The metarunner's `additionalParameters` contains:

```text
-Dsonar.projectKey=%SONAR_PROJECT_KEY%
-Dsonar.projectName=%SONAR_PROJECT_NAME%
-Dsonar.projectVersion=%SONAR_PROJECT_VERSION%
-Dsonar.qualitygate.wait=%SONAR_QUALITY_GATE_WAIT%
-Dsonar.host.url=%SONAR_SERVER_URL%
-Dsonar.token=%SONAR_SERVER_TOKEN%
%SONAR_EXTRA_PARAMETERS%
```

> **Important:** The `additionalParameters` field requires parameters to be separated by **newlines**, not spaces. Using space-separated parameters can result in unresolved hosts or silently dropped parameters. The `SONAR_EXTRA_PARAMETERS` output from `CalculateSonarParameters` is already newline-separated.

Additional parameters are configured directly on the metarunner:

| Metarunner Parameter        | Maps to                  | Description                                     |
|-----------------------------|--------------------------|-------------------------------------------------|
| `SONAR_PROJECT_SOURCES`     | `sonar.sources`          | Source directories                              |
| `SONAR_PROJECT_BINARIES`    | `sonar.java.binaries`    | Compiled class directories                      |
| `SONAR_PROJECT_TESTS`       | `sonar.tests`            | Test source directories                         |
| `SONAR_PROJECT_MODULES`     | `sonar.modules`          | Sub-modules in a multi-module project           |

### 2. Gradle SonarQube Plugin

When using the [Gradle SonarQube plugin](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/), pass `SONAR_PARAMETERS` as system properties on the Gradle command line:

```bash
./gradlew sonar %SONAR_PARAMETERS%
```

`SONAR_PARAMETERS` contains the similar parameters as `additionalParameters` in the SonarRunner metarunner, but with additional Gradle-specific ones, for example: `-Dsonar.gradle.scanAll=true`.


#### `SONAR_TASK`

The `CalculateSonarParameters` metarunner also sets `SONAR_TASK`. This parameter is set to a reference to a TeamCity parameter: `%SONAR_GRADLE_TASK%` for Gradle components and `%SONAR_MAVEN_GOAL%` for Maven components, when the component is Java/Kotlin-based and uses a modern Java version (17 or 21, including components in the mismatch-java-version list). Otherwise, it is set to an empty string.

This allows composing it into the default `GRADLE_TASK` TeamCity parameter so that the Sonar analysis task is included only when applicable:

```text
build %SONAR_TASK% publish
```

When `SONAR_TASK` is empty the command becomes `build  publish`, which Gradle handles normally.

### 3. Maven SonarQube Plugin

When using the [Maven SonarQube plugin](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/), pass `SONAR_PARAMETERS` as system properties on the Maven command line:

```bash
mvn org.sonarsource.scanner.maven:sonar-maven-plugin:{version}:sonar %SONAR_PARAMETERS%
```

`SONAR_PARAMETERS` contains the similar parameters as `additionalParameters` in the SonarRunner metarunner, but with additional Maven-specific ones, for example: `-Dsonar.maven.scanAll=true`.

Similarly, `SONAR_TASK` can be composed into Maven goals:

```text
clean install %SONAR_TASK%
```

